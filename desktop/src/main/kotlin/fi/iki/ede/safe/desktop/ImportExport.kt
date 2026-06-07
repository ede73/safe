@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBHelper
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

object BackupImporter {
    fun importFromXml(backupContent: String, passwordStr: String, db: DBHelper): Int {
        val lines = backupContent.trimIndent().trim().lines()
        if (lines.size < 5) {
            throw IllegalArgumentException("Invalid backup file format: Less than 5 lines")
        }

        val salt = Salt(lines[0].trim().hexToByteArray())
        val masterKeyIv = lines[1].trim().hexToByteArray()
        val masterKeyCipher = lines[2].trim().hexToByteArray()
        val backupIv = lines[3].trim().hexToByteArray()
        val backupCipher = lines[4].trim().hexToByteArray()

        val encryptedMasterKey = IVCipherText(masterKeyIv, masterKeyCipher)
        val encryptedBackup = IVCipherText(backupIv, backupCipher)

        // Derive key from password
        val pbkdf2Key = KeyManagement.generatePBKDF2AESKey(
            salt,
            CipherUtilities.KEY_ITERATION_COUNT,
            Password(passwordStr),
            CipherUtilities.KEY_LENGTH_BITS
        )

        // Decrypt master key
        val decryptedMasterKey = KeyManagement.decryptMasterKey(
            pbkdf2Key,
            encryptedMasterKey
        )

        // Decrypt backup content
        val decryptedXmlBytes = korlibs.crypto.AES.decryptAesCbc(
            encryptedBackup.cipherText,
            decryptedMasterKey.values,
            encryptedBackup.iv,
            korlibs.crypto.Padding.PKCS7Padding
        )
        val decryptedXml = decryptedXmlBytes.decodeToString()

        // Set skipPrepopulate to prevent checkPrepopulate from running before KeyStoreHelper is initialized
        db.skipPrepopulate = true

        // Setup the master key in KeyStoreHelper and database
        KeyStoreHelper.importExistingEncryptedMasterKey(
            fi.iki.ede.crypto.SaltedPassword(salt, Password(passwordStr)),
            encryptedMasterKey
        )
        db.storeSaltAndEncryptedMasterKey(salt, encryptedMasterKey)

        // Clear existing categories and entries in mock DB
        db.clearAllData()

        val factory = XMLInputFactory.newInstance()
        val reader = factory.createXMLStreamReader(StringReader(decryptedXml))

        var currentCategoryId: Long? = null
        var currentSiteEntry: DecryptableSiteEntry? = null
        var passwordsCount = 0

        try {
            while (reader.hasNext()) {
                val event = reader.next()
                when (event) {
                    XMLStreamConstants.START_ELEMENT -> {
                        val name = reader.localName
                        when (name) {
                            "category" -> {
                                val ivName = reader.getAttributeValue(null, "iv_name")
                                val cipherName = reader.getAttributeValue(null, "cipher_name")
                                if (!ivName.isNullOrBlank() && !cipherName.isNullOrBlank()) {
                                    val cat = DecryptableCategoryEntry().apply {
                                        encryptedName = IVCipherText(ivName.hexToByteArray(), cipherName.hexToByteArray())
                                    }
                                    currentCategoryId = db.addCategory(cat)
                                }
                            }
                            "item" -> {
                                val categoryId = currentCategoryId ?: throw IllegalStateException("Item outside category")
                                currentSiteEntry = DecryptableSiteEntry(categoryId).apply {
                                    val idAttr = reader.getAttributeValue(null, "ID")
                                    if (idAttr != null) {
                                        id = idAttr.toLongOrNull()
                                    }
                                    val deletedAttr = reader.getAttributeValue(null, "deleted")
                                    if (deletedAttr != null) {
                                        deleted = deletedAttr.toLongOrNull() ?: 0L
                                    }
                                }
                            }
                            "description", "website", "username", "password", "note", "photo", "extension" -> {
                                 val ivAttr = reader.getAttributeValue(null, "iv")
                                 val passChangedAttr = if (name == "password") reader.getAttributeValue(null, "changed") else null
                                 
                                 val text = reader.elementText ?: ""
                                 val encryptedValue = if (ivAttr.isNullOrBlank() || text.isBlank()) {
                                     IVCipherText.getEmpty()
                                 } else {
                                     IVCipherText(ivAttr.hexToByteArray(), text.hexToByteArray())
                                 }

                                val entry = currentSiteEntry
                                if (entry != null) {
                                    when (name) {
                                        "description" -> entry.description = encryptedValue
                                        "website" -> entry.website = encryptedValue
                                        "username" -> entry.username = encryptedValue
                                        "password" -> {
                                            entry.password = encryptedValue
                                            if (passChangedAttr != null) {
                                                try {
                                                    entry.passwordChangedDate = passChangedAttr.toLongOrNull()?.let {
                                                        fi.iki.ede.dateutils.DateUtils.unixEpochSecondsToInstant(it)
                                                    }
                                                } catch (e: Exception) {
                                                    // Ignore date issues
                                                }
                                            }
                                        }
                                        "note" -> entry.note = encryptedValue
                                        "photo" -> entry.photo = encryptedValue
                                        "extension" -> entry.extensions = encryptedValue
                                    }
                                }
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        val name = reader.localName
                        if (name == "item") {
                            val entry = currentSiteEntry
                            if (entry != null) {
                                db.addSiteEntry(entry)
                                passwordsCount++
                            }
                            currentSiteEntry = null
                        }
                    }
                }
            }
        } finally {
            db.skipPrepopulate = false
            reader.close()
        }
        return passwordsCount
    }
}
