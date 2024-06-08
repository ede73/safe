package fi.iki.ede.safe.backupandrestore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Attributes
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Elements
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import kotlinx.coroutines.CancellationException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

class RestoreDatabase : ExportConfig(ExportVersion.V1) {
    private fun XmlPullParser.getAttributeValue(
        attribute: Attributes,
        prefix: String? = null
    ): String {
        val attrName = if (prefix == null) attribute.value else "$prefix${attribute.value}"
        return getAttributeValue(null, attrName) ?: ""
    }

    // After some recent update while restoring, date parsing fails due to non breakable space
    // Wasn't able to track IN THE EMULATOR where it comes from
    private fun XmlPullParser.getTrimmedAttributeValue(
        attribute: Attributes,
        prefix: String? = null
    ): String =
        getAttributeValue(attribute, prefix).trim()

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
        dbHelper: DBHelper,
        verifyOldBackupRestoration: (backupCreated: ZonedDateTime, lastBackupDone: ZonedDateTime) -> Boolean
    ): Int {
        val myParser = XmlPullParserFactory.newInstance().newPullParser()

        val backupData = BackupData(StringReader(backup.trimIndent().trim()).readLines())

        val db = dbHelper.beginRestoration()

        try {
            dbHelper.storeSaltAndEncryptedMasterKey(
                backupData.getSalt(),
                backupData.getEncryptedMasterKey()
            )
            // body
            myParser.setInput(
                getDocumentStream(backupData, userPassword),
                null
            )

            val passwords = parseXML(dbHelper, db, myParser, verifyOldBackupRestoration)
            LoginHandler.passwordLogin(context, userPassword)
            return passwords
        } catch (ex: Exception) {
            db.endTransaction()
            throw ex
        }
    }

    private fun getDocumentStream(
        backupData: BackupData,
        userPassword: Password
    ) = ByteArrayInputStream(
        KeyStoreHelperFactory.getKeyStoreHelper().decryptByteArray( // Keystore needed (new key)
            backupData.getEncryptedBackup(),
            decryptMasterKey(backupData, userPassword)
        )
    )


    private fun decryptMasterKey(
        backupData: BackupData,
        userPassword: Password,
    ) = KeyManagement.decryptMasterKey(
        generatePBKDF2AESKey(
            backupData.getSalt(),
            KEY_ITERATION_COUNT,
            userPassword,
            KEY_LENGTH_BITS
        ), backupData.getEncryptedMasterKey()
    )


    inline fun <reified T : Enum<T>, V> valueOrNull(value: V, valueSelector: (T) -> V): T? =
        enumValues<T>().find { valueSelector(it) == value }


    private fun parseXML(
        dbHelper: DBHelper,
        db: SQLiteDatabase,
        myParser: XmlPullParser,
        verifyOldBackupRestoration: (backupCreated: ZonedDateTime, lastBackupDone: ZonedDateTime) -> Boolean,
    ): Int {
        fun XmlPullParser.getEncryptedAttribute(name: Attributes): IVCipherText {
            val iv = getTrimmedAttributeValue(name, ATTRIBUTE_PREFIX_IV)
            val cipher = getTrimmedAttributeValue(name, ATTRIBUTE_PREFIX_CIPHER)
            if (iv.isNotBlank() && cipher.isNotBlank()) {
                return IVCipherText(iv.hexToByteArray(), cipher.hexToByteArray())
            }
            return IVCipherText.getEmpty()
        }

        fun XmlPullParser.maybeGetText(gotTextNode: (encryptedText: IVCipherText) -> Unit) {
            val iv = getTrimmedAttributeValue(ExportConfig.Companion.Attributes.IV)
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

        val path = mutableListOf<Elements?>()
        var category: DecryptableCategoryEntry? = null
        var password: DecryptableSiteEntry? = null
        var passwords = 0
        while (myParser.eventType != XmlPullParser.END_DOCUMENT) {
            when (myParser.eventType) {
                XmlPullParser.START_TAG -> {
                    path.add(valueOrNull<Elements, String>(myParser.name) { it.value })

                    when (path) {
                        listOf(Elements.ROOT_PASSWORD_SAFE) -> {
                            val rawVersion = myParser.getTrimmedAttributeValue(
                                Attributes.ROOT_PASSWORD_SAFE_VERSION
                            )
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
                            val creationTime = myParser.getTrimmedAttributeValue(
                                Attributes.ROOT_PASSWORD_SAFE_CREATION_TIME
                            ).toLongOrNull()?.let {
                                DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
                            }
                            val lastBackupDone = Preferences.getLastBackupTime()
                            // TODO: until above can be mocked..feeling lazy
                            //val creationTime: ZonedDateTime? = null
                            // if we know the backup creation time AND we known when a backup
                            // was LAST done, we can warn used not to restore older copy
                            if (creationTime?.let { backupCreatedTime ->
                                    lastBackupDone
                                        ?.let { lastBackupTime -> backupCreatedTime < lastBackupTime }
                                } == true) {
                                if (!verifyOldBackupRestoration(creationTime, lastBackupDone!!)) {
                                    // user wants to cancel
                                    throw CancellationException()
                                }
                            }
                        }

                        listOf(Elements.ROOT_PASSWORD_SAFE, Elements.CATEGORY) -> {
                            require(category == null) { "Must have no pending objects" }
                            category = DecryptableCategoryEntry()
                            category.encryptedName =
                                myParser.getEncryptedAttribute(Attributes.CATEGORY_NAME)
                            category.id = dbHelper.addCategory(category)
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM
                        ) -> {
                            require(category != null) { "Must have category" }
                            require(password == null) { "Must not have password" }
                            password = DecryptableSiteEntry(category.id!!)
                            passwords++
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_DESCRIPTION
                        ) -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.description = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_WEBSITE
                        ) -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.website = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_USERNAME
                        ) -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.username = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_PASSWORD
                        ) -> {
                            require(password != null) { "Must have password entry" }

                            val changed =
                                myParser.getTrimmedAttributeValue(
                                    Attributes.CATEGORY_ITEM_PASSWORD_CHANGED
                                )
                            if (changed.isNotBlank()) {
                                try {
                                    password.passwordChangedDate =
                                        changed.toLongOrNull()?.let {
                                            DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
                                        } ?: DateUtils.newParse(changed)
                                } catch (ex: DateTimeParseException) {
                                    // silently fail, parse failure ain't critical
                                    // and no corrective measure here, passwords are more important
                                    if (BuildConfig.DEBUG) {
                                        Log.e(TAG, "Failed parsing password change data ($changed)")
                                    }
                                }
                            }
                            myParser.maybeGetText {
                                password!!.password = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_NOTE
                        ) -> {
                            require(password != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                password!!.note = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM,
                            Elements.CATEGORY_ITEM_PHOTO
                        ) -> {
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
                    when (path) {
                        listOf(Elements.ROOT_PASSWORD_SAFE) -> {
                            // All should be finished now
                            db.setTransactionSuccessful()
                            db.endTransaction()
                        }

                        listOf(Elements.ROOT_PASSWORD_SAFE, Elements.CATEGORY) -> {
                            require(category != null) { "Must have category entry" }
                            category = null
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM
                        ) -> {
                            require(password != null) { "Must have password entry" }
                            dbHelper.addPassword(password)
                            password = null
                        }
                    }
                    path.remove(valueOrNull<Elements, String>(myParser.name) { it.value })
                }
            }
            myParser.next()
        }
        // sorry linter, you are mistaken, ain't 0 all the time
        return passwords
    }

    companion object {
        const val TAG = "Restore"
    }
}