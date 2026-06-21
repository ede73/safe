package fi.iki.ede.backup

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt

import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.Timeout
import okio.buffer
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.time.ExperimentalTime
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

/**
 * TODO: Add HMAC
 *
 * If you EVER introduce a breaking change (namespace, remove elements, rename attributes)
 * Make sure to increase the version code. Linter will highlight places to fix
 */
@ExperimentalTime
class BackupDatabase : ExportConfig(ExportVersion.V1) {
    // Addressed PR10 comment: Use Okio BufferedSink signature for KMP compliance
    @VisibleForTesting(PRIVATE)
    fun generateXMLExport(
        outputSink: BufferedSink,
        categoriesList: List<DecryptableCategoryEntry>,
        softDeletedEntries: Set<DecryptableSiteEntry>,
        siteEntryGPMMappings: Map<DBID, Set<DBID>>,
        allSavedGPMs: Set<SavedGPM>,
        getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>
    ) {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        // Addressed PR10 comment: Convert BufferedSink to java.io.OutputStream at boundary for XMLSerializer, writing in US-ASCII
        serializer.setOutput(outputSink.outputStream(), "US-ASCII")

        serializer.startTag(Elements.ROOT_PASSWORD_SAFE)
            .plainTextAttribute(
                Attributes.ROOT_PASSWORD_SAFE_VERSION,
                currentVersion.version
            )
            .plainTextAttribute(
                Attributes.ROOT_PASSWORD_SAFE_CREATION_TIME,
                DateUtils.toUnixSeconds().toString()
            )

        for (category in categoriesList) {
            serializer.startTagWithIVCipherAttribute(
                Elements.CATEGORY,
                makePair(Attributes.CATEGORY_NAME, category.encryptedName)
            )
            // TODO: wow, XMLSerializer will run out of memory if massive load of photos
            // makes sense, need better XML serializer here , one day (made test of 1026 massive photo entries)
            for (encryptedPassword in getSiteEntriesOfCategory(category.id!!)) {
                serializer.writeSiteEntry(encryptedPassword)
            }
            softDeletedEntries.filter { it.categoryId == category.id }.forEach { deletedPassword ->
                serializer.writeSiteEntry(deletedPassword)
            }
            serializer.endTag(Elements.CATEGORY)
        }

        // dump all imported passwords!
        // TODO: use data model! (proper channels)
        if (allSavedGPMs.isNotEmpty()) {
            serializer.startTag(Elements.IMPORTS)
            val gpmIdToSiteEntry =
                // TODO: use data model!
                siteEntryGPMMappings.flatMap { (a, bSet) -> bSet.map { b -> b to a } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, v) -> v.toSet() }
            serializer.startTag(Elements.IMPORTS_GPM)
            allSavedGPMs.forEach { savedGPM ->
                serializer.writeGPMEntry(
                    savedGPM,
                    gpmIdToSiteEntry.getOrDefault(savedGPM.id!!, emptySet<DBID>())
                )
            }
            serializer.endTag(Elements.IMPORTS_GPM)
            serializer.endTag(Elements.IMPORTS)
        }

        serializer.endTag(Elements.ROOT_PASSWORD_SAFE)
        serializer.endDocument()
        // Addressed PR10 comment: Flush Okio sink
        outputSink.flush()
    }

    @Suppress("SameParameterValue")
    private fun makePair(
        name: Attributes,
        encryptedValue: IVCipherText?
    ) = if (encryptedValue == null || encryptedValue.isEmpty()) {
        null
    } else {
        Pair(name, encryptedValue)
    }

    companion object {
        const val TAG = "Backup"
        const val MIME_TYPE_BACKUP = "text/xml"
        fun backup(
            categoriesList: List<DecryptableCategoryEntry>,
            softDeletedEntries: Set<DecryptableSiteEntry>,
            getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>,
            siteEntryGPMMappings: Map<DBID, Set<DBID>>,
            allSavedGPMs: Set<SavedGPM>,
            finalSink: Sink
        ) {
            val bufferedSink = finalSink.buffer()

            val (salt, key) = DBHelperFactory.getDBHelper().fetchSaltAndEncryptedMasterKey()

            // 1. Write salt
            bufferedSink.writeUtf8(salt.salt.toHexString() + "\n")
            // 2. Write master key IV + ciphertext
            bufferedSink.writeUtf8(key.iv.toHexString() + "\n")
            bufferedSink.writeUtf8(key.cipherText.toHexString() + "\n")
            // 3. Write chunked marker
            bufferedSink.writeUtf8("STREAMING_CHUNKED_V1\n")

            // Addressed PR10 comment: Use imported KeyStoreHelperFactory instead of FQDN
            val helper = KeyStoreHelperFactory.getKeyStoreHelper()
            // Addressed PR10 comment: Use pure Okio ChunkingEncryptingSink with okio.Buffer decoration
            val chunkingSink = ChunkingEncryptingSink(bufferedSink) { plaintext ->
                helper.encrypterProvider(plaintext)
            }.buffer()

            // 5. Generate XML directly into chunkingSink
            BackupDatabase().generateXMLExport(
                chunkingSink,
                categoriesList,
                softDeletedEntries,
                siteEntryGPMMappings,
                allSavedGPMs,
                getSiteEntriesOfCategory
            )

            chunkingSink.close()
        }
    }
}

// Addressed PR10 comment: Custom chunking Sink utilizing okio.Buffer, avoiding raw arraycopy and java.io streams
private class ChunkingEncryptingSink(
    private val delegate: BufferedSink,
    private val chunkSize: Long = 65536, // 64KB chunks
    private val encrypt: (ByteArray) -> IVCipherText
) : Sink {
    private val buffer = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val space = chunkSize - buffer.size
            if (space <= 0) {
                flushBuffer()
                continue
            }
            val toWrite = minOf(remaining, space)
            buffer.write(source, toWrite)
            remaining -= toWrite
            if (buffer.size >= chunkSize) {
                flushBuffer()
            }
        }
    }

    private fun flushBuffer() {
        if (buffer.size > 0) {
            val plaintext = buffer.readByteArray()
            val encrypted = encrypt(plaintext)
            val line = "${encrypted.iv.toHexString()}:${encrypted.cipherText.toHexString()}\n"
            delegate.writeUtf8(line)
        }
    }

    override fun flush() {
        delegate.flush()
    }

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() {
        flushBuffer()
        delegate.flush()
        delegate.close()
    }
}