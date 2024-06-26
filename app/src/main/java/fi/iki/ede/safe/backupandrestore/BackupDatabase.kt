package fi.iki.ede.safe.backupandrestore

import android.text.TextUtils
import android.util.Log
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Attributes
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Elements
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
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
    private fun generateXMLExport(): String {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        val xmlStringWriter = StringWriter()
        serializer.setOutput(xmlStringWriter)

        serializer.startTag(Elements.ROOT_PASSWORD_SAFE)
            .attribute(Attributes.ROOT_PASSWORD_SAFE_VERSION, currentVersion.version)
            .attribute(
                Attributes.ROOT_PASSWORD_SAFE_CREATION_TIME, DateUtils.toUnixSeconds(
                    ZonedDateTime.now()
                ).toString()
            )

        for (category in DataModel.getCategories()) {
            serializer.startTagWithIVCipherAttribute(
                Elements.CATEGORY,
                makePair(Attributes.CATEGORY_NAME, category.encryptedName)
            )
            for (encryptedPassword in DataModel.getCategorysSiteEntries(category.id!!)) {
                serializer.writeSiteEntry(encryptedPassword)
            }
            DataModel.softDeletedStateFlow.value.toSet().forEach { deletedPassword ->
                serializer.writeSiteEntry(deletedPassword)
            }
            serializer.endTag(Elements.CATEGORY)
        }

        // dump all imported passwords!
        // TODO: use datamodel! (proper channels)
        val gpms = DataModel._savedGPMs
        if (gpms.isNotEmpty()) {
            serializer.startTag(Elements.IMPORTS)
            val gpmIdToSiteEntry =
                // TODO: use datamodel!
                DBHelperFactory.getDBHelper().fetchAllSiteEntryGPMMappings()
                    .flatMap { (a, bSet) -> bSet.map { b -> b to a } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, v) -> v.toSet() }
            serializer.startTag(Elements.IMPORTS_GPM)
            gpms.forEach { savedGPM ->
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
        println(makeThisStreaming)
        if (makeThisStreaming.contains(" ")) {
            Log.e(TAG, "Oh no, XML export has non breakable spaces")
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

        suspend fun backup() = flow<ByteArray> {
            emit(BackupDatabase().generateXMLExport().toByteArray())
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