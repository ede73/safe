package fi.iki.ede.safe.backupandrestore

import android.text.TextUtils
import android.util.Log
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.HexString
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.toHexString
import fi.iki.ede.safe.model.DataModel
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter

/**
 * TODO: Add HMAC
 *
 * If you EVER introduce a breaking change (namespace, remove elements, rename attributes)
 * Make sure to increase the version code. Linter will highlight places to fix
 */
class Backup : ExportConfig(ExportVersion.V1) {
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()

    private fun XmlSerializer.addTagAndCData(
        name: String,
        encryptedValue: IVCipherText,
        attr: Pair<String, String>? = null
    ): XmlSerializer {
        return startTag(null, name)
            .let {
                if (attr?.first != null) {
                    it.attribute(null, attr.first, attr.second)
                }
                this
            }
            .let {
                it.attribute(null, "iv", encryptedValue.iv.toHexString())
                it.text(encryptedValue.cipherText.toHexString())
                this
            }
            .endTag(null, name)
    }

    @Suppress("SameParameterValue")
    private fun XmlSerializer.startTagWithAttribute(
        name: String,
        attr: Pair<String, IVCipherText>? = null
    ): XmlSerializer {
        return startTag(null, name)
            .let {
                if (attr?.first != null) {
                    it.attribute(null, "iv_${attr.first}", attr.second.iv.toHexString())
                    it.attribute(null, "cipher_${attr.first}", attr.second.cipherText.toHexString())
                }
                this
            }
    }

    @Suppress("SameParameterValue")
    private fun makePair(name: String, encryptedValue: IVCipherText?): Pair<String, IVCipherText>? {
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

        serializer.startTag(null, ELEMENT_ROOT_PASSWORD_SAFE)
            .attribute(null, ATTRIBUTE_ROOT_PASSWORD_SAFE_VERSION, currentVersion.version)

        for (category in DataModel.getCategories()) {
            serializer.startTagWithAttribute(
                ELEMENT_CATEGORY,
                makePair(ATTRIBUTE_CATEGORY_NAME, category.encryptedName)
            )
            for (encryptedPassword in DataModel.getCategorysPasswords(category.id!!)) {
                serializer.writePasswordEntry(encryptedPassword)
            }
            serializer.endTag(null, ELEMENT_CATEGORY)
        }

        serializer.endTag(null, ELEMENT_ROOT_PASSWORD_SAFE)
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
        decryptablePassword: DecryptablePasswordEntry,
    ) {
        startTag(null, ELEMENT_CATEGORY_ITEM)
        addTagAndCData(ELEMENT_CATEGORY_ITEM_DESCRIPTION, decryptablePassword.description)
        addTagAndCData(ELEMENT_CATEGORY_ITEM_WEBSITE, decryptablePassword.website)
        addTagAndCData(ELEMENT_CATEGORY_ITEM_USERNAME, decryptablePassword.username)

        addTagAndCData(
            ELEMENT_CATEGORY_ITEM_PASSWORD, decryptablePassword.password,
            decryptablePassword.passwordChangedDate?.let {
                val formattedDate = DateUtils.newFormat(it)
                println("date of ${decryptablePassword.passwordChangedDate} is $formattedDate")
                Pair(ATTRIBUTE_CATEGORY_ITEM_PASSWORD_CHANGED, formattedDate)
            }
        )

        addTagAndCData(ELEMENT_CATEGORY_ITEM_NOTE, decryptablePassword.note)

        if (decryptablePassword.photo != IVCipherText.getEmpty()) {
            addTagAndCData(ELEMENT_CATEGORY_ITEM_PHOTO, decryptablePassword.photo)
        }
        endTag(null, ELEMENT_CATEGORY_ITEM)
    }

    companion object {
        const val TAG = "Backup"
        const val MIME_TYPE_BACKUP = "text/xml"
    }
}