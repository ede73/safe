package fi.iki.ede.safe.backupandrestore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.LoginHandler
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.time.format.DateTimeParseException
import javax.crypto.spec.SecretKeySpec


class Restore : ExportConfig(ExportVersion.V1) {

    private fun XmlPullParser.getTrimmedAttributeValue(name: String): String =
        getAttributeValue("", name)?.trim() ?: ""

    data class BackupData(val data: List<String>) {
        fun getSalt(): Salt = Salt(data[0].hexToByteArray())
        fun getEncryptedMasterKey(): IVCipherText =
            IVCipherText(data[1].hexToByteArray(), data[2].hexToByteArray())

        fun getEncryptedBackup(): IVCipherText =
            IVCipherText(data[3].hexToByteArray(), data[4].hexToByteArray())
    }

    fun doRestore(
        context: Context,
        backup: String,
        userPassword: Password,
        dbHelper: DBHelper
    ): Int {
        val xmlFactoryObject = XmlPullParserFactory.newInstance()
        val myParser = xmlFactoryObject.newPullParser()

        val sr = BackupData(StringReader(backup.trimIndent().trim()).readLines())
        val encryptedKey = sr.getEncryptedMasterKey()

        val db = dbHelper.beginRestoration()

        try {
            dbHelper.storeSaltAndEncryptedMasterKey(sr.getSalt(), encryptedKey)
            // body
            myParser.setInput(
                getDocumentStream(sr, userPassword),
                null
            )

            val passwords = parseXML(dbHelper, db, myParser)
            LoginHandler.passwordLogin(context, userPassword)
            return passwords
        } catch (ex: Exception) {
            db.endTransaction()
            throw ex
        }
    }

    private fun getDocumentStream(
        sr: BackupData,
        userPassword: Password
    ): ByteArrayInputStream {
        val document = KeyStoreHelperFactory.getKeyStoreHelper().decryptByteArray(
            sr.getEncryptedBackup(),
            decryptMasterKey(sr, userPassword)
        )
        return ByteArrayInputStream(
            document
        )
    }

    private fun decryptMasterKey(
        sr: BackupData,
        userPassword: Password,
    ): SecretKeySpec {
        return KeyManagement.decryptMasterKey(
            KeyStoreHelper.generatePBKDF2(
                sr.getSalt(),
                userPassword
            ), sr.getEncryptedMasterKey()
        )
    }

    private fun parseXML(
        dbHelper: DBHelper,
        db: SQLiteDatabase,
        myParser: XmlPullParser,
    ): Int {
        fun XmlPullParser.getEncryptedAttribute(name: String): IVCipherText {
            val iv = getTrimmedAttributeValue("iv_$name")
            val cipher = getTrimmedAttributeValue("cipher_$name")
            if (iv.isNotBlank() && cipher.isNotBlank()) {
                return IVCipherText(iv.hexToByteArray(), cipher.hexToByteArray())
            }
            return IVCipherText.getEmpty()
        }

        fun XmlPullParser.maybeGetText(gotTextNode: (encryptedText: IVCipherText) -> Unit) {
            val iv = getTrimmedAttributeValue("iv")
            next()
            if (eventType == XmlPullParser.TEXT && text != null && iv.isNotBlank()) {
                gotTextNode.invoke(
                    IVCipherText(
                        iv.hexToByteArray(),
                        text.hexToByteArray()
                    )
                )
            }
        }

        val path = mutableListOf<String>()
        var category: DecryptableCategoryEntry? = null
        var password: DecryptablePasswordEntry? = null
        var passwords = 0
        while (myParser.eventType != XmlPullParser.END_DOCUMENT) {
            when (myParser.eventType) {
                XmlPullParser.START_TAG -> {
                    path.add(myParser.name)

                    when (path.joinToString(separator = ".")) {
                        "PasswordSafe" -> {
                            val rawVersion = myParser.getTrimmedAttributeValue("version")
                            val version =
                                ExportVersion.entries.firstOrNull { it.version == rawVersion }
                                    ?: throw IllegalArgumentException("Unsupported export version ($rawVersion)")
                            if (version != currentVersion) {
                                // Don't remove this template below, right now it is defunct, but
                                // if we ever need to change the version code, linter will cause error
                                // here and remind you to handle the situation
                                when (version) {
                                    ExportVersion.V1 -> {
                                        // current version - currently
                                    }
                                }
                            }
                        }

                        "PasswordSafe.category" -> {
                            require(category == null) { "Must have no pending objects" }
                            category = DecryptableCategoryEntry()
                            category.encryptedName = myParser.getEncryptedAttribute("name")
                            category.id = dbHelper.addCategory(category)
                        }

                        "PasswordSafe.category.item" -> {
                            require(category != null) { "Must have category" }
                            require(password == null) { "Must not have password" }
                            password = DecryptablePasswordEntry(category.id!!)
                            passwords++
                        }

                        "PasswordSafe.category.item.description" -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.description = it
                            }
                        }

                        "PasswordSafe.category.item.website" -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.website = it
                            }
                        }

                        "PasswordSafe.category.item.username" -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.username = it
                            }
                        }

                        "PasswordSafe.category.item.password" -> {
                            require(password != null) { "Must have password entry" }
                            val changed = myParser.getTrimmedAttributeValue("changed")
                            if (changed.isNotBlank()) {
                                try {
                                    password.passwordChangedDate = DateUtils.newParse(changed)
                                } catch (ex: DateTimeParseException) {
                                    // silently fail, parse failure ain't critical
                                    // and no corrective measure here, passwords are more important
                                    if (BuildConfig.DEBUG) {
                                        Log.e(TAG, "Failed parsing password change data")
                                    }
                                }
                            }
                            myParser.maybeGetText {
                                password!!.password = it
                            }
                        }

                        "PasswordSafe.category.item.note" -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.note = it
                            }
                        }

                        "PasswordSafe.category.item.photo" -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.photo = it
                            }
                        }
                    }
                }
            }

            // butt ugly, but nextText() is broken, there's no peek and
            // suggested next()/text() breaks on e.g. <note></note> it skips the end ..unless doing this
            when (myParser.eventType) {
                XmlPullParser.END_TAG -> {
                    when (path.joinToString(separator = ".")) {
                        "PasswordSafe" -> {
                            // All should be finished now
                            db.setTransactionSuccessful()
                            db.endTransaction()
                        }

                        "PasswordSafe.category" -> {
                            require(category != null) { "Must have category entry" }
                            category = null
                        }

                        "PasswordSafe.category.item" -> {
                            require(password != null) { "Must have password entry" }
                            dbHelper.addPassword(password)
                            password = null
                        }
                    }
                    path.remove(myParser.name)
                }
            }
            myParser.next()
        }
        return passwords
    }

    companion object {
        const val TAG = "Restore"
    }
}