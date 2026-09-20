package fi.iki.ede.backup

import fi.iki.ede.db.DBTransaction
import fi.iki.ede.backup.ExportConfig.Companion.Attributes
import fi.iki.ede.backup.ExportConfig.Companion.Elements
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.keystore.KMPKey
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.*
import fi.iki.ede.db.cxf.CXFAccount
import fi.iki.ede.db.cxf.CXFImport
import fi.iki.ede.db.cxf.CXFPasskey
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseRecordException
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import fi.iki.ede.backup.xml.XmlPullParser
import fi.iki.ede.backup.xml.XmlPullParserFactory
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExperimentalTime
class RestoreDatabase : ExportConfig(ExportVersion.V1) {
    fun doRestore(
        backupSource: Source,
        userPassword: Password,
        dbHelper: DBHelper,
        lastBackupDone: Instant?,
        linkSaveGPMAndSiteEntry: (DBID, DBID) -> Unit,
        addSavedGPM: (SavedGPM) -> Unit,
        passwordLogin: (password: Password) -> Boolean,
        reportProgress: (categories: Int?, passwords: Int?, message: RestorationProgress?) -> Unit,
        verifyUserWantForOldBackup: (backupCreated: Instant, lastBackupDone: Instant) -> Boolean,
    ): Int {
        reportProgress(null, null, RestorationProgress.BEGIN_RESTORATION)
        val myParser = XmlPullParserFactory.newInstance().newPullParser()

        val bufferedSource = backupSource.buffer()
        var saltLine = ""
        while (true) {
            val l = bufferedSource.readUtf8Line() ?: throw IllegalArgumentException("Missing salt")
            val trimmed = l.trim()
            if (trimmed.isNotEmpty()) {
                saltLine = trimmed
                break
            }
        }
        val ivMasterLine = (bufferedSource.readUtf8Line() ?: throw IllegalArgumentException("Missing master key IV")).trim()
        val cipherMasterLine = (bufferedSource.readUtf8Line() ?: throw IllegalArgumentException("Missing master key ciphertext")).trim()
        val line4 = (bufferedSource.readUtf8Line() ?: throw IllegalArgumentException("Missing line 4")).trim()

        val salt = Salt(saltLine.hexToByteArray())
        val encryptedMasterKey = IVCipherText(ivMasterLine.hexToByteArray(), cipherMasterLine.hexToByteArray())

        val db = dbHelper.beginRestoration()

        try {
            dbHelper.storeSaltAndEncryptedMasterKey(salt, encryptedMasterKey)

            // Decrypt the master key using the password
            val masterKey = decryptMasterKey(salt, encryptedMasterKey, userPassword)

            val ivBackup = line4.hexToByteArray()
            val cipherMasterLine2 = bufferedSource.readUtf8Line() ?: throw IllegalArgumentException("Missing backup data ciphertext")
            val cipherBackup = cipherMasterLine2.hexToByteArray()

            val helper = KeyStoreHelperFactory.getKeyStoreHelper()
            val decrypted = helper.decrypterProviderWithKey(
                IVCipherText(ivBackup, cipherBackup),
                masterKey
            )
            val xmlInputStream = Buffer().write(decrypted)
            myParser.setInput(xmlInputStream)

            reportProgress(null, null, RestorationProgress.PROCESS_BACKUP)
            val passwords = parseXML(
                dbHelper,
                db,
                myParser,
                lastBackupDone,
                linkSaveGPMAndSiteEntry,
                addSavedGPM,
                verifyUserWantForOldBackup,
                reportProgress,
            )
            passwordLogin(userPassword)
            reportProgress(null, null, RestorationProgress.FINISHED_WITH_BACKUP)
            return passwords
        } catch (ex: Exception) {
            Logger.e(TAG, "Restoration failed!", ex)
            ex.printStackTrace()
            firebaseRecordException("Failed to restore", ex)
            db.endTransaction()
            reportProgress(null, null, RestorationProgress.FAILED_ROLLBACK)
            throw ex
        }
    }

    private fun decryptMasterKey(
        salt: Salt,
        encryptedMasterKey: IVCipherText,
        userPassword: Password,
    ) = KeyManagement.decryptMasterKey(
        generatePBKDF2AESKey(
            salt,
            KEY_ITERATION_COUNT,
            userPassword,
            KEY_LENGTH_BITS
        ), encryptedMasterKey
    )


    @Suppress("DEPRECATION")
    private fun parseXML(
        dbHelper: DBHelper,
        db: DBTransaction,
        myParser: XmlPullParser,
        lastBackupDone: Instant?,
        linkSaveGPMAndSiteEntry: (DBID, DBID) -> Unit,
        addSavedGPM: (SavedGPM) -> Unit,
        verifyOldBackupRestoration: (backupCreated: Instant, lastBackupDone: Instant) -> Boolean,
        reportProgress: (categories: Int?, passwords: Int?, message: RestorationProgress?) -> Unit,
    ): Int {
        val path = mutableListOf<Elements?>()
        var category: DecryptableCategoryEntry? = null
        var siteEntry: DecryptableSiteEntry? = null
        val deletedSiteEntriesToRestore =
            mutableSetOf<DecryptableSiteEntry>()
        val gpmLinkedToDeletedSiteEntries = mutableMapOf<DBID, MutableSet<DBID>>()
        val categoryIDs = mutableSetOf<DBID>()
        var readGPM: SavedGPM? = null
        val readGPMMapsToPasswords: MutableMap<Long, Set<Long>> = mutableMapOf()
        var passwords = 0
        var categories = 0
        val restoredCxfAccounts = mutableListOf<CXFAccount>()
        val restoredCxfImports = mutableListOf<Pair<String, CXFImport>>()
        val restoredCxfPasskeys = mutableListOf<Pair<String, CXFPasskey>>()
        while (myParser.eventType != XmlPullParser.END_DOCUMENT) {
            when (myParser.eventType) {
                XmlPullParser.START_TAG -> {
                    path.add(valueOrNull<Elements, String>(myParser.name ?: "") { it.value })
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
                                DateUtils.unixEpochSecondsToInstant(
                                    it
                                )
                            }
                            // TODO: until above can be mocked..feeling lazy
                            //val creationTime: ZonedDateTime? = null
                            // if we know the backup creation time AND we known when a backup
                            // was LAST done, we can warn used not to restore older copy
                            if (creationTime?.let { backupCreatedTime ->
                                    lastBackupDone
                                        ?.let { lastBackupTime -> backupCreatedTime < lastBackupTime }
                                } == true) {
                                reportProgress(null, null, RestorationProgress.RESTORING_OLD_BACKUP)
                                if (!verifyOldBackupRestoration(creationTime, lastBackupDone!!)) {
                                    // user wants to cancel
                                    throw CancellationException()
                                }
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_CXF,
                            Elements.IMPORTS_CXF_ACCOUNT
                        ) -> {
                            val cxfAccountId = myParser.getTrimmedAttributeValue(Attributes.CXF_ACCOUNT_ID)
                            val emailIvCipher = myParser.getEncryptedAttribute(Attributes.CXF_ACCOUNT_EMAIL)
                            if (cxfAccountId.isNotBlank()) {
                                restoredCxfAccounts.add(CXFAccount(cxfAccountId = cxfAccountId, encryptedEmail = emailIvCipher))
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_CXF,
                            Elements.IMPORTS_CXF_IMPORT
                        ) -> {
                            val accCxfId = myParser.getTrimmedAttributeValue(Attributes.CXF_ACCOUNT_ID)
                            val cxfItemId = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_ID)
                            val type = myParser.getTrimmedAttributeValue(Attributes.CXF_ITEM_TYPE)
                            val name = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_NAME)
                            val url = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_URL)
                            val username = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_USERNAME)
                            val password = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_PASSWORD)
                            val note = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_NOTE)
                            val hash = myParser.getTrimmedAttributeValue(Attributes.CXF_ITEM_HASH)
                            val flaggedIgnored = myParser.getTrimmedAttributeValue(Attributes.CXF_ITEM_FLAGGED_IGNORED) == "1"

                            val item = CXFImport(
                                accountId = 0L,
                                encryptedCxfItemId = cxfItemId,
                                type = type.ifBlank { "password" },
                                encryptedName = name,
                                encryptedUrl = url,
                                encryptedUsername = username,
                                encryptedPassword = password,
                                encryptedNote = note,
                                hash = hash,
                                flaggedIgnored = flaggedIgnored
                            )
                            restoredCxfImports.add(accCxfId to item)
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_CXF,
                            Elements.IMPORTS_CXF_PASSKEY
                        ) -> {
                            val accCxfId = myParser.getTrimmedAttributeValue(Attributes.CXF_ACCOUNT_ID)
                            val cxfItemId = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_ID)
                            val rpId = myParser.getTrimmedAttributeValue(Attributes.CXF_PASSKEY_RELYING_PARTY)
                            val name = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_NAME)
                            val url = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_URL)
                            val username = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_USERNAME)
                            val credId = myParser.getEncryptedAttribute(Attributes.CXF_PASSKEY_CREDENTIAL_ID)
                            val uHandle = myParser.getEncryptedAttribute(Attributes.CXF_PASSKEY_USER_HANDLE)
                            val note = myParser.getEncryptedAttribute(Attributes.CXF_ITEM_NOTE)
                            val hash = myParser.getTrimmedAttributeValue(Attributes.CXF_ITEM_HASH)
                            val flaggedIgnored = myParser.getTrimmedAttributeValue(Attributes.CXF_ITEM_FLAGGED_IGNORED) == "1"

                            val passkey = CXFPasskey(
                                accountId = 0L,
                                encryptedCxfItemId = cxfItemId,
                                rpId = rpId,
                                encryptedName = name,
                                encryptedUrl = url,
                                encryptedUsername = username,
                                encryptedCredentialId = credId,
                                encryptedUserHandle = uHandle,
                                encryptedNote = note,
                                hash = hash,
                                flaggedIgnored = flaggedIgnored
                            )
                            restoredCxfPasskeys.add(accCxfId to passkey)
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
                                myParser.getTrimmedAttributeValue(Attributes.IMPORTS_GPM_ITEM_MAP_TO_SITE_ENTRY)
                                    .takeIf { it.isNotBlank() }?.split(",")
                                    ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
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
                            categoryIDs.add(category.id!!)
                            categories++
                            reportProgress(categories, null, null)
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY
                        ) -> {
                            require(category != null) { "Must have category" }
                            require(siteEntry == null) { "Must not have siteEntry" }
                            siteEntry = DecryptableSiteEntry(category.id!!)
                            myParser.getTrimmedAttributeValue(Attributes.SITE_ENTRY_ID)
                                .toLongOrNull()?.let {
                                    siteEntry.id = it
                                }
                            myParser.getTrimmedAttributeValue(Attributes.SITE_ENTRY_DELETED)
                                .toLongOrNull()?.let {
                                    val normalizedDeleted = DateUtils.normalizeTimestampToSeconds(it)
                                    siteEntry.deleted = normalizedDeleted
                                }
                            passwords++
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_DESCRIPTION
                        ) -> {
                            require(siteEntry != null) { "Must have siteEntry" }
                            myParser.maybeGetText {
                                siteEntry!!.description = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_WEBSITE
                        ) -> {
                            require(siteEntry != null) { "Must have siteEntry" }
                            myParser.maybeGetText {
                                siteEntry!!.website = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_USERNAME
                        ) -> {
                            require(siteEntry != null) { "Must have siteEntry" }
                            myParser.maybeGetText {
                                siteEntry!!.username = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_PASSWORD
                        ) -> {
                            require(siteEntry != null) { "Must have siteEntry" }

                            val changed =
                                myParser.getTrimmedAttributeValue(Attributes.SITE_ENTRY_PASSWORD_CHANGED)
                            if (changed.isNotBlank()) {
                                try {
                                    siteEntry.passwordChangedDate =
                                        changed.toLongOrNull()?.let {
                                            DateUtils.unixEpochSecondsToInstant(
                                                it
                                            )
                                        } ?: DateUtils.newParse(changed)
                                } catch (ex: IllegalArgumentException) {
                                    firebaseRecordException(
                                        "Failed to parse date ($changed)",
                                        ex
                                    )
                                }
                            }
                            myParser.maybeGetText {
                                siteEntry!!.password = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_NOTE
                        ) -> {
                            require(siteEntry != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                siteEntry!!.note = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_PHOTO
                        ) -> {
                            require(siteEntry != null) { "Must have password entry" }
                            myParser.maybeGetText {
                                siteEntry!!.photo = it
                            }
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.CATEGORY,
                            Elements.SITE_ENTRY,
                            Elements.SITE_ENTRY_EXTENSION,
                        ) -> {
                            require(siteEntry != null) { "Must have siteEntry" }
                            myParser.maybeGetText {
                                siteEntry!!.extensions = it
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
                                readGPMMapsToPasswords.forEach { (gpmId, passwords) ->
                                    passwords.forEach { passwordId ->
                                        if (passwordId in deletedSiteEntriesToRestore.map { it.id }) {
                                            // this GPM is linked to a deleted site entry!
                                            // we don't know the ID yet!
                                            gpmLinkedToDeletedSiteEntries.getOrPut(gpmId) { mutableSetOf<DBID>() }
                                                .add(passwordId)
                                        } else {
                                            linkSaveGPMAndSiteEntry(passwordId, gpmId)
                                        }
                                    }
                                }
                                readGPMMapsToPasswords.clear()
                            }
                            deletedSiteEntriesToRestore.forEach { deletedSiteEntry ->
                                try {
                                    // Deleted password always belong to category (in the back up file)
                                    // since they are contained in the category element
                                    val oldId = deletedSiteEntry.id!!
                                    deletedSiteEntry.id = null
                                    val newId = dbHelper.addSiteEntry(deletedSiteEntry)
                                    gpmLinkedToDeletedSiteEntries.forEach { (gpmId, deletedSiteEntryIds) ->
                                        if (oldId in deletedSiteEntryIds) {
                                            linkSaveGPMAndSiteEntry(newId, gpmId)
                                        }
                                    }
                                } catch (ex: Exception) {
                                    firebaseRecordException(
                                        "Failed to store deleted site entry",
                                        ex
                                    )
                                }
                            }

                            if (restoredCxfAccounts.isNotEmpty() || restoredCxfImports.isNotEmpty() || restoredCxfPasskeys.isNotEmpty()) {
                                val cxfDb = dbHelper.database
                                kotlinx.coroutines.runBlocking {
                                    val existingAccounts = cxfDb.cxfAccountDao().getAll().associateBy { it.cxfAccountId }
                                    val accountCxfIdToDbId = mutableMapOf<String, Long>()

                                    for (acc in restoredCxfAccounts) {
                                        val dbId = existingAccounts[acc.cxfAccountId]?.id
                                            ?: cxfDb.cxfAccountDao().insert(acc)
                                        accountCxfIdToDbId[acc.cxfAccountId] = dbId
                                    }

                                    for ((accCxfId, item) in restoredCxfImports) {
                                        val dbAccId = accountCxfIdToDbId[accCxfId] ?: continue
                                        val existingImports = cxfDb.cxfImportDao().getByAccountId(dbAccId)
                                        val alreadyExists = existingImports.any { it.hash == item.hash }
                                        if (!alreadyExists) {
                                            cxfDb.cxfImportDao().insert(item.copy(accountId = dbAccId))
                                        }
                                    }

                                    for ((accCxfId, passkey) in restoredCxfPasskeys) {
                                        val dbAccId = accountCxfIdToDbId[accCxfId] ?: continue
                                        val existingPasskeys = cxfDb.cxfPasskeyDao().getByAccountId(dbAccId)
                                        val alreadyExists = existingPasskeys.any { it.hash == passkey.hash }
                                        if (!alreadyExists) {
                                            cxfDb.cxfPasskeyDao().insert(passkey.copy(accountId = dbAccId))
                                        }
                                    }
                                }
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
                            Elements.SITE_ENTRY
                        ) -> {
                            require(siteEntry != null) { "Must have password entry" }
                            reportProgress(null, passwords, null)
                            if (siteEntry.deleted > 0) {
                                // we can't restore deleted site entries AHEAD of time
                                // due to potential ID conflicts, we'll gotta do it after
                                // all live site entries have been restored
                                // (and just assign next available ID)
                                deletedSiteEntriesToRestore.add(siteEntry)
                            } else {
                                dbHelper.addSiteEntry(siteEntry)
                            }
                            siteEntry = null
                        }

                        listOf(
                            Elements.ROOT_PASSWORD_SAFE,
                            Elements.IMPORTS,
                            Elements.IMPORTS_GPM,
                            Elements.IMPORTS_GPM_ITEM
                        ) -> {
                            require(readGPM != null) { "Must have GPM entry" }
                            // if GPM is linked to a deleted site entry,
                            // we don't know yet the ID, since link is done in affiliation table
                            // the code resilience code is in linkSaveGPMAndSiteEntry above
                            addSavedGPM(readGPM)
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
