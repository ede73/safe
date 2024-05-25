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
 * Create a backup document. Format as follows:
 * - HEXADECIMAL SALT
 * - Master key encrypted with PBKDF2 user generated key
 * - IV + Document XML encrypted
 *
 * Decryption can be done as follows:
 * 1) Extract SALT from the file
 * 2) Generate PBKDF2 key: DEC_KEY=$(openssl enc -aes-256-cbc -P -md sha256 -S $SALT -iter 20000 -pbkdf2|grep key|cut -d= -f2)
 * 3) Extract encrypted master key from the file (with IV)
 * 4) Extract IV from the encrypted master key (256/16 first bytes): IV=$(echo "$ENCRYPTED_IVD_MASTERKEY" | cut -c1-32)
 * 5) Remove IV from the encrypted master key: ENCRYPTED_MASTERKEY=$(echo "$ENCRYPTED_IVD_MASTERKEY" | cut -c33-)
 * 6) Decrypt master key: MASTER_KEY=$(echo -n $ENCRYPTED_MASTERKEY|xxd -r -p -|base64 | openssl enc -aes-256-cbc -d -a -iv $IV -K $DEC_KEY -nosalt|xxd -c222 -p
 * 7) Extract the document from the file
 * 8) Extract IV from the document: DOCUMENT_IV=$(echo $DOCUMENT|cut -c1-32)
 * 9) Extract cipher text (the document): DOCUMENT=$(echo $DOCUMENT|cut -c33-)
 * 10) Decrypt the document: echo -n $DOCUMENT|xxd -r -p -|base64| openssl enc -aes-256-cbc -d -a -iv $DOCUMENT_IV -K $MASTER_KEY -nosalt
 *
 * Done!
 *
 * TODO: TO AVOID processing (decrypting/re-encrypting data) actually let it be encrypted (it's double encryption)
 *
 * ie. encrypted_with_master_key{..<description>encrypted_with_master_key{description}</description>..}
 * it is ugly, to avoid ever processing unencrypted data (even if we're streaming) and preventing memory dumps
 * TODO: Add HMAC
 */
class Backup {
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
                if (encryptedValue != null) it.text(encryptedValue.cipherText.toHexString())
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
            .attribute(null, "version", "1")

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
        // TODO: Instead KS, use..
        // {salt}
        // {PKCS#12 PBE AES}
        // encryption

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