package fi.iki.ede.safe.backupandrestore

import android.text.TextUtils
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

        serializer.startTag(null, "PasswordSafe")
            .attribute(null, "version", currentVersion.version)

        for (category in DataModel.getCategories()) {
            serializer.startTagWithAttribute(
                CATEGORY_TAG,
                makePair("name", category.encryptedName)
            )
            for (encryptedPassword in DataModel.getCategorysPasswords(category.id!!)) {
                serializer.writePasswordEntry(encryptedPassword)
            }
            serializer.endTag(null, CATEGORY_TAG)
        }

        serializer.endTag(null, "PasswordSafe")
        serializer.endDocument()
        val makeThisStreaming = xmlStringWriter.toString()
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
        startTag(null, "item")
        addTagAndCData("description", decryptablePassword.description)
        addTagAndCData("website", decryptablePassword.website)
        addTagAndCData("username", decryptablePassword.username)
        val plaintextPasswordChangedDate =
            if (decryptablePassword.passwordChangedDate == null) null else
                Pair("changed", DateUtils.newFormat(decryptablePassword.passwordChangedDate!!))
        addTagAndCData(
            "password", decryptablePassword.password,
            plaintextPasswordChangedDate
        )

        addTagAndCData("note", decryptablePassword.note)

        if (decryptablePassword.photo != IVCipherText.getEmpty()) {
            addTagAndCData("photo", decryptablePassword.photo)
        }
        endTag(null, "item")
    }

    companion object {
        private const val CATEGORY_TAG = "category"
        const val MIME_TYPE_BACKUP = "text/xml"
    }
}