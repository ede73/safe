package fi.iki.ede.backup

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.support.toHexString
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

/**
 * TODO: Add HMAC
 *
 * If you EVER introduce a breaking change (namespace, remove elements, rename attributes)
 * Make sure to increase the version code. Linter will highlight places to fix
 */
@ExperimentalTime
class BackupDatabase : ExportConfig(ExportVersion.V1) {
    @VisibleForTesting(PRIVATE)
    fun generateXMLExport(
        outputStream: java.io.OutputStream,
        categoriesList: List<DecryptableCategoryEntry>,
        softDeletedEntries: Set<DecryptableSiteEntry>,
        siteEntryGPMMappings: Map<DBID, Set<DBID>>,
        allSavedGPMs: Set<SavedGPM>,
        getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>
    ) {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        serializer.setOutput(outputStream, "UTF-8")

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
        outputStream.flush()
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
            val outputStream = finalSink.buffer().outputStream()

            val (salt, key) = DBHelperFactory.getDBHelper().fetchSaltAndEncryptedMasterKey()

            // 1. Write salt
            outputStream.write((salt.salt.toHexString() + "\n").toByteArray(Charsets.UTF_8))
            // 2. Write master key IV + ciphertext
            outputStream.write((key.iv.toHexString() + "\n").toByteArray(Charsets.UTF_8))
            outputStream.write((key.cipherText.toHexString() + "\n").toByteArray(Charsets.UTF_8))

            // 3. Write chunked marker
            outputStream.write(("STREAMING_CHUNKED_V1\n").toByteArray(Charsets.UTF_8))

            // 4. Wrap output in a chunking output stream
            val helper = fi.iki.ede.crypto.keystore.KeyStoreHelperFactory.getKeyStoreHelper()
            val chunkingOut = ChunkingEncryptingOutputStream(outputStream) { plaintext ->
                helper.encrypterProvider(plaintext)
            }

            // 5. Generate XML directly into chunkingOut
            BackupDatabase().generateXMLExport(
                chunkingOut,
                categoriesList,
                softDeletedEntries,
                siteEntryGPMMappings,
                allSavedGPMs,
                getSiteEntriesOfCategory
            )

            chunkingOut.close()
        }
    }
}

private class ChunkingEncryptingOutputStream(
    private val out: java.io.OutputStream,
    private val chunkSize: Int = 65536, // 64KB chunks
    private val encrypt: (ByteArray) -> IVCipherText
) : java.io.OutputStream() {
    private val buffer = ByteArray(chunkSize)
    private var count = 0

    override fun write(b: Int) {
        buffer[count++] = b.toByte()
        if (count >= chunkSize) {
            flushBuffer()
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var bytesWritten = 0
        while (bytesWritten < len) {
            val space = chunkSize - count
            val toWrite = minOf(space, len - bytesWritten)
            System.arraycopy(b, off + bytesWritten, buffer, count, toWrite)
            count += toWrite
            bytesWritten += toWrite
            if (count >= chunkSize) {
                flushBuffer()
            }
        }
    }

    private fun flushBuffer() {
        if (count > 0) {
            val chunk = ByteArray(count)
            System.arraycopy(buffer, 0, chunk, 0, count)
            val encrypted = encrypt(chunk)
            val line = "${encrypted.iv.toHexString()}:${encrypted.cipherText.toHexString()}\n"
            out.write(line.toByteArray(Charsets.UTF_8))
            count = 0
        }
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        flushBuffer()
        out.flush()
        out.close()
    }
}