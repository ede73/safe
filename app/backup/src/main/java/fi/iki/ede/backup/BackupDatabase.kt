package fi.iki.ede.backup

import android.text.TextUtils
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.transform
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringWriter
import java.time.ZonedDateTime

/**
 * TODO: Add HMAC
 *
 * If you EVER introduce a breaking change (namespace, remove elements, rename attributes)
 * Make sure to increase the version code. Linter will highlight places to fix
 */
class BackupDatabase : ExportConfig(ExportVersion.V1) {
    fun generateXMLExport(
        categoriesList: List<DecryptableCategoryEntry>,
        softDeletedEntries: Set<DecryptableSiteEntry>,
        siteEntryGPMMappings: Map<DBID, Set<DBID>>,
        allSavedGPMs: Set<SavedGPM>,
        getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>
    ): String {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        val xmlStringWriter = StringWriter()
        serializer.setOutput(xmlStringWriter)

        serializer.startTag(Elements.ROOT_PASSWORD_SAFE)
            .plainTextAttribute(
                Attributes.ROOT_PASSWORD_SAFE_VERSION,
                currentVersion.version
            )
            .plainTextAttribute(
                Attributes.ROOT_PASSWORD_SAFE_CREATION_TIME,
                DateUtils.toUnixSeconds(
                    ZonedDateTime.now()
                ).toString()
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
        val makeThisStreaming = xmlStringWriter.toString()
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, makeThisStreaming)
        }
        if (makeThisStreaming.contains(" ")) {
            Logger.e(TAG, "Oh no, XML export has non breakable spaces")
        }
        assert(!TextUtils.isEmpty(makeThisStreaming)) { "Something is broken, XML serialization produced empty file" }
        return makeThisStreaming.trim()
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

        suspend fun backup(
            categoriesList: List<DecryptableCategoryEntry>,
            softDeletedEntries: Set<DecryptableSiteEntry>,
            getSiteEntriesOfCategory: (categoryId: DBID) -> List<DecryptableSiteEntry>,
            siteEntryGPMMappings: Map<DBID, Set<DBID>>,
            allSavedGPMs: Set<SavedGPM>,
        ) = flow<ByteArray> {
            emit(
                BackupDatabase().generateXMLExport(
                    categoriesList,
                    softDeletedEntries,
                    siteEntryGPMMappings,
                    allSavedGPMs,
                    getSiteEntriesOfCategory
                ).toByteArray()
            )
        }.transform<ByteArray, IVCipherText> { ba ->
            val encrypter = KeyStoreHelperFactory.getEncrypter()
            emit(encrypter(ba))
        }.flowOn(Dispatchers.Default).transform<IVCipherText, String> { encrypted ->
            val (salt, currentEncryptedMasterKey) = DBHelperFactory.getDBHelper()
                .fetchSaltAndEncryptedMasterKey()
            emit(salt.toHex())
            emit("\n")
            emit(currentEncryptedMasterKey.iv.toHexString())
            emit("\n")
            emit(currentEncryptedMasterKey.cipherText.toHexString())
            emit("\n")
            emit(encrypted.iv.toHexString())
            emit("\n")
            emit(encrypted.cipherText.toHexString())
            emit("\n")
        }.fold(StringBuilder()) { accumulator: StringBuilder, value: String ->
            accumulator.append(value)
        }
    }
}