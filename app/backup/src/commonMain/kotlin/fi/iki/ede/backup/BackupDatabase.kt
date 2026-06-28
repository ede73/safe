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
import fi.iki.ede.backup.xml.XmlPullParserFactory
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
        serializer.setOutput(outputSink, "US-ASCII")

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
                    gpmIdToSiteEntry[savedGPM.id!!] ?: emptySet()
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

            // 3. Generate XML in memory using okio.Buffer
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

            // 4. Encrypt the entire XML bytes
            val helper = KeyStoreHelperFactory.getKeyStoreHelper()
            val encrypted = helper.encrypterProvider(xmlBytes)

            // 5. Write backup data IV + ciphertext
            bufferedSink.writeUtf8(encrypted.iv.toHexString() + "\n")
            bufferedSink.writeUtf8(encrypted.cipherText.toHexString() + "\n")
            bufferedSink.flush()
        }
    }
}