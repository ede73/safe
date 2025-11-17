package fi.iki.ede.backup

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
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
        buffer: Buffer,
        categoriesList: List<DecryptableCategoryEntry>,
        softDeletedEntries: Set<DecryptableSiteEntry>,
        siteEntryGPMMappings: Map<DBID, Set<DBID>>,
        allSavedGPMs: Set<SavedGPM>,
        getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>
    ) {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        serializer.setOutput(buffer.outputStream(), "ASCII")

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
            softDeletedEntries.toSet().forEach { deletedPassword ->
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
                    gpmIdToSiteEntry.getOrDefault(savedGPM.id!!, emptySet())
                )
            }
            serializer.endTag(Elements.IMPORTS_GPM)
            serializer.endTag(Elements.IMPORTS)
        }

        serializer.endTag(Elements.ROOT_PASSWORD_SAFE)
        serializer.endDocument()
        buffer.flush()
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
            val hexSink = HexEncodingSink(finalSink.buffer())

            val (salt, key) = DBHelperFactory.getDBHelper().fetchSaltAndEncryptedMasterKey()

            hexSink.write(salt)
            hexSink.write(key)
            hexSink.flush()

            hexSink.flush()

            val xmlBuf = Buffer()
            BackupDatabase().generateXMLExport(
                xmlBuf,
                categoriesList,
                softDeletedEntries,
                siteEntryGPMMappings,
                allSavedGPMs,
                getSiteEntriesOfCategory
            )
            val xmlBytes = xmlBuf.readByteArray()

            val encBytes = KeyStoreHelperFactory.encrypterProvider(xmlBytes)

            hexSink.write(encBytes)
            hexSink.flush()
        }
    }
}

private class HexEncodingSink(private val out: BufferedSink) : Sink {
    fun write(salt: Salt) {
        write(salt.salt)
        out.writeUtf8("\n")
    }

    fun write(cipherText: IVCipherText) {
        write(cipherText.iv)
        out.writeUtf8("\n")
        write(cipherText.cipherText)
        out.writeUtf8("\n")
    }

    fun write(bytes: ByteArray) {
        write(Buffer().write(bytes), bytes.size.toLong())
    }

    override fun write(source: Buffer, byteCount: Long) {
        // consume the bytes from source, convert to hex, write UTF-8 hex string to out
        val bytes = source.readByteArray(byteCount)
        // fast hex conversion
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            val hex1 = Character.forDigit((v ushr 4), 16)
            val hex2 = Character.forDigit((v and 0x0F), 16)
            sb.append(hex1).append(hex2)
        }
        out.writeUtf8(sb.toString())
    }

    override fun flush() {
        out.flush()
    }

    override fun close() {
        out.close()
    }

    override fun timeout(): Timeout = out.timeout()
}