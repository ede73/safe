package fi.iki.ede.safe.backupandrestore

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Attributes
import fi.iki.ede.safe.backupandrestore.ExportConfig.Companion.Elements
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
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
    data class BackupEncryptionKeys(val data: List<String>) {
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
        reportProgress: (categories: Int?, passwords: Int?, message: String?) -> Unit,
        verifyUserWantForOldBackup: (backupCreated: ZonedDateTime, lastBackupDone: ZonedDateTime) -> Boolean
    ): Int {
        reportProgress(null, null, "Begin restoration")
        val myParser = XmlPullParserFactory.newInstance().newPullParser()

        val backupEncryptionKeys =
            BackupEncryptionKeys(StringReader(backup.trimIndent().trim()).readLines())

        val db = dbHelper.beginRestoration()

        try {
            // TODO: this also has inner transaction
            dbHelper.storeSaltAndEncryptedMasterKey(
                backupEncryptionKeys.getSalt(),
                backupEncryptionKeys.getEncryptedMasterKey()
            )
            // body
            myParser.setInput(
                getDocumentStream(backupEncryptionKeys, userPassword),
                null
            )

            reportProgress(null, null, "Process backup")
            val passwords =
                parseXML(dbHelper, db, myParser, verifyUserWantForOldBackup, reportProgress)
            LoginHandler.passwordLogin(context, userPassword)
            reportProgress(null, null, "Finished with backup")
            return passwords
        } catch (ex: Exception) {
            db.endTransaction()
            reportProgress(null, null, "Something failed, rollback")
            throw ex
        }
    }

    private fun getDocumentStream(
        backupEncryptionKeys: BackupEncryptionKeys,
        userPassword: Password
    ) = ByteArrayInputStream(
        KeyStoreHelperFactory.getKeyStoreHelper().decryptByteArray( // Keystore needed (new key)
            backupEncryptionKeys.getEncryptedBackup(),
            decryptMasterKey(backupEncryptionKeys, userPassword)
        )
    )


    private fun decryptMasterKey(
        backupEncryptionKeys: BackupEncryptionKeys,
        userPassword: Password,
    ) = KeyManagement.decryptMasterKey(
        generatePBKDF2AESKey(
            backupEncryptionKeys.getSalt(),
            KEY_ITERATION_COUNT,
            userPassword,
            KEY_LENGTH_BITS
        ), backupEncryptionKeys.getEncryptedMasterKey()
    )


    private fun parseXML(
        dbHelper: DBHelper,
        db: SQLiteDatabase,
        myParser: XmlPullParser,
        verifyOldBackupRestoration: (backupCreated: ZonedDateTime, lastBackupDone: ZonedDateTime) -> Boolean,
        reportProgress: (categories: Int?, passwords: Int?, message: String?) -> Unit,
    ): Int {
        val path = mutableListOf<Elements?>()
        var category: DecryptableCategoryEntry? = null
        var password: DecryptableSiteEntry? = null
        var readGPM: SavedGPM? = null
        val readGPMMapsToPasswords: MutableMap<Long, Set<Long>> = mutableMapOf()
        var passwords = 0
        var categories = 0
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
                                reportProgress(null, null, "Restoring old backup")
                                if (!verifyOldBackupRestoration(creationTime, lastBackupDone!!)) {
                                    // user wants to cancel
                                    throw CancellationException()
                                }
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_GPM,
                            Elements.IMPORTS_GPM_ITEM
                        ) -> {
                            // actually import GPM entries
                            readGPM = SavedGPM.makeFromEncryptedStringFields(
                                myParser.getTrimmedAttributeValue(Attributes.IMPORTS_GPM_ITEM_ID)
                                    .toLong(),
                                myParser.getEncryptedAttribute(Attributes.IMPORTS_GPM_ITEM_NAME),
                                myParser.getEncryptedAttribute(Attributes.IMPORTS_GPM_ITEM_URL),
                                myParser.getEncryptedAttribute(Attributes.IMPORTS_GPM_ITEM_USERNAME),
                                myParser.getEncryptedAttribute(Attributes.IMPORTS_GPM_ITEM_PASSWORD),
                                myParser.getEncryptedAttribute(Attributes.IMPORTS_GPM_ITEM_NOTE),
                                myParser.getTrimmedAttributeValue(Attributes.IMPORTS_GPM_ITEM_STATUS)
                                    .toInt() == 1,
                                myParser.getTrimmedAttributeValue(Attributes.IMPORTS_GPM_ITEM_HASH)
                            )

                            val mapsToPasswords =
                                myParser.getTrimmedAttributeValue(Attributes.IMPORTS_GPM_ITEM_MAP_TO_PASSWORDS)
                                    .takeIf { it.isNotBlank() }?.split(",")
                                    ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet<Long>()
                            if (mapsToPasswords.isNotEmpty()) {
                                readGPMMapsToPasswords[readGPM.id!!] = mapsToPasswords
                            }
                        }

                        listOf(Elements.ROOT_PASSWORD_SAFE, Elements.CATEGORY) -> {
                            require(category == null) { "Must have no pending objects" }
                            category = DecryptableCategoryEntry()
                            category.encryptedName =
                                myParser.getEncryptedAttribute(Attributes.CATEGORY_NAME)
                            category.id = dbHelper.addCategory(category)
                            categories++
                            reportProgress(categories, null, null)

                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.CATEGORY_ITEM
                        ) -> {
                            require(category != null) { "Must have category" }
                            require(password == null) { "Must not have password" }
                            password = DecryptableSiteEntry(category.id!!)
                            myParser.getTrimmedAttributeValue(Attributes.CATEGORY_ITEM_ID)
                                .toLongOrNull()?.let {
                                    password!!.id = it
                                }
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
                                myParser.getTrimmedAttributeValue(Attributes.CATEGORY_ITEM_PASSWORD_CHANGED)
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
                            if (readGPMMapsToPasswords.isNotEmpty()) {
                                readGPMMapsToPasswords.forEach { (gpmid, passwords) ->
                                    passwords.forEach { password ->
                                        dbHelper.linkSaveGPMAndSiteEntry(password, gpmid)
                                    }
                                }
                                readGPMMapsToPasswords.clear()
                            }
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
                            reportProgress(null, passwords, null)
                            dbHelper.addSiteEntry(password)
                            password = null
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_GPM,
                            Elements.IMPORTS_GPM_ITEM
                        ) -> {
                            require(readGPM != null) { "Must have GPM entry" }
                            dbHelper.addSavedGPM(readGPM)
                            readGPM = null
                        }
                    }
                    // removeLast() broken on build tools 35
                    path.removeLastOrNull()
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