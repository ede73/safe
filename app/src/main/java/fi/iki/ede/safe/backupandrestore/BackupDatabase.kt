package fi.iki.ede.safe.backupandrestore

import android.text.TextUtils
import android.util.Log
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.HexString
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.toHexString
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Attributes
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Elements
import fi.iki.ede.safe.model.DataModel
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.time.ZoneOffset

/**
 * TODO: Add HMAC
 *
 * If you EVER introduce a breaking change (namespace, remove elements, rename attributes)
 * Make sure to increase the version code. Linter will highlight places to fix
 */
class BackupDatabase : ExportConfig(ExportVersion.V1) {
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()

    private fun XmlSerializer.addTagAndCData(
        name: Elements,
        encryptedValue: IVCipherText,
        attr: Pair<Attributes, String>? = null
    ): XmlSerializer {
        return startTag(name)
            .let {
                if (attr?.first != null) {
                    it.attribute(attr.first, attr.second)
                }
                this
            }
            .let {
                it.attribute(Attributes.IV, encryptedValue.iv.toHexString())
                it.text(encryptedValue.cipherText.toHexString())
                this
            }
            .endTag(name)
    }

    private fun XmlSerializer.endTag(name: Elements) = endTag(null, name.value)
    private fun XmlSerializer.startTag(name: Elements) = startTag(null, name.value)
    private fun XmlSerializer.attribute(name: Attributes, value: String) =
        attribute(null, name.value, value)

    private fun XmlSerializer.attribute(name: Attributes, prefix: String, value: String) =
        attribute(null, "${prefix}${name.value}", value)


    @Suppress("SameParameterValue")
    private fun XmlSerializer.startTagWithAttribute(
        name: Elements,
        attr: Pair<Attributes, IVCipherText>? = null
    ): XmlSerializer {
        return startTag(name)
            .let {
                if (attr?.first != null) {
                    it.attribute(attr.first, ATTRIBUTE_PREFIX_IV, attr.second.iv.toHexString())
                    it.attribute(
                        attr.first,
                        ATTRIBUTE_PREFIX_CIPHER,
                        attr.second.cipherText.toHexString()
                    )
                }
                this
            }
    }

    @Suppress("SameParameterValue")
    private fun makePair(
        name: Attributes,
        encryptedValue: IVCipherText?
    ): Pair<Attributes, IVCipherText>? {
        return if (encryptedValue == null || encryptedValue.isEmpty()) {
            null
        } else {
            Pair(name, encryptedValue)
        }
    }

    fun generate(salt: Salt, currentEncryptedMasterKey: IVCipherText): HexString {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()
        val xmlStringWriter = StringWriter()
        serializer.setOutput(xmlStringWriter)

        serializer.startTag(Elements.ROOT_PASSWORD_SAFE)
            .attribute(Attributes.ROOT_PASSWORD_SAFE_VERSION, currentVersion.version)

        for (category in DataModel.getCategories()) {
            serializer.startTagWithAttribute(
                Elements.CATEGORY,
                makePair(Attributes.CATEGORY_NAME, category.encryptedName)
            )
            for (encryptedPassword in DataModel.getCategorysPasswords(category.id!!)) {
                serializer.writePasswordEntry(encryptedPassword)
            }
            serializer.endTag(Elements.CATEGORY)
        }

        serializer.endTag(Elements.ROOT_PASSWORD_SAFE)
        serializer.endDocument()
        val makeThisStreaming = xmlStringWriter.toString()

        if (makeThisStreaming.contains(" ")) {
            Log.e(TAG, "Oh no, XML export has non breakable spaces")
        }
        assert(!TextUtils.isEmpty(makeThisStreaming)) { "Something is broken, XML serialization produced empty file" }
        val encryptedBackup = ks.encryptByteArray(makeThisStreaming.toByteArray())

        val backup = StringWriter()
        backup.appendLine(salt.toHex())
        backup.appendLine(currentEncryptedMasterKey.iv.toHexString())
        backup.appendLine(currentEncryptedMasterKey.cipherText.toHexString())
        backup.appendLine(encryptedBackup.iv.toHexString())
        backup.appendLine(encryptedBackup.cipherText.toHexString())
        return backup.toString()
    }

    private fun XmlSerializer.writePasswordEntry(
        decryptablePassword: DecryptableSiteEntry,
    ) {
        startTag(Elements.CATEGORY_ITEM)
        addTagAndCData(Elements.CATEGORY_ITEM_DESCRIPTION, decryptablePassword.description)
        addTagAndCData(Elements.CATEGORY_ITEM_WEBSITE, decryptablePassword.website)
        addTagAndCData(Elements.CATEGORY_ITEM_USERNAME, decryptablePassword.username)

        addTagAndCData(
            Elements.CATEGORY_ITEM_PASSWORD, decryptablePassword.password,
            decryptablePassword.passwordChangedDate?.let {
                Pair(
                    Attributes.CATEGORY_ITEM_PASSWORD_CHANGED,
                    it.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond().toString()
                )
            }
        )

        addTagAndCData(Elements.CATEGORY_ITEM_NOTE, decryptablePassword.note)

        if (decryptablePassword.photo != IVCipherText.getEmpty()) {
            addTagAndCData(Elements.CATEGORY_ITEM_PHOTO, decryptablePassword.photo)
        }
        endTag(Elements.CATEGORY_ITEM)
    }

    companion object {
        const val TAG = "Backup"
        const val MIME_TYPE_BACKUP = "text/xml"
    }
}