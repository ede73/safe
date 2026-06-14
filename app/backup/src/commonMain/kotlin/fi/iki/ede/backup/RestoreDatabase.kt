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
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseRecordException
import kotlinx.coroutines.CancellationException
import okio.Buffer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExperimentalTime
class RestoreDatabase : ExportConfig(ExportVersion.V1) {
    fun doRestore(
        context: Any?,
        backupReader: java.io.Reader,
        userPassword: Password,
        dbHelper: DBHelper,
        lastBackupDone: Instant?,
        linkSaveGPMAndSiteEntry: (DBID, DBID) -> Unit,
        addSavedGPM: (SavedGPM) -> Unit,
        passwordLogin: (context: Any?, password: Password) -> Boolean,
        reportProgress: (categories: Int?, passwords: Int?, message: String?) -> Unit,
        verifyUserWantForOldBackup: (backupCreated: Instant, lastBackupDone: Instant) -> Boolean,
    ): Int {
        reportProgress(null, null, "Begin restoration")
        val myParser = XmlPullParserFactory.newInstance().newPullParser()

        val bufferedReader = if (backupReader is java.io.BufferedReader) backupReader else java.io.BufferedReader(backupReader)
        var saltLine = ""
        while (true) {
            val l = bufferedReader.readLine() ?: throw IllegalArgumentException("Missing salt")
            val trimmed = l.trim()
            if (trimmed.isNotEmpty()) {
                saltLine = trimmed
                break
            }
        }
        val ivMasterLine = (bufferedReader.readLine() ?: throw IllegalArgumentException("Missing master key IV")).trim()
        val cipherMasterLine = (bufferedReader.readLine() ?: throw IllegalArgumentException("Missing master key ciphertext")).trim()
        val line4 = (bufferedReader.readLine() ?: throw IllegalArgumentException("Missing line 4")).trim()

        val salt = Salt(saltLine.hexToByteArray())
        val encryptedMasterKey = IVCipherText(ivMasterLine.hexToByteArray(), cipherMasterLine.hexToByteArray())

        val db = dbHelper.beginRestoration()

        try {
            dbHelper.storeSaltAndEncryptedMasterKey(salt, encryptedMasterKey)

            // Decrypt the master key using the password
            val masterKey = decryptMasterKey(salt, encryptedMasterKey, userPassword)

            val xmlInputStream = if (line4 == "STREAMING_CHUNKED_V1") {
                // Wrap in ChunkingDecryptingInputStream
                val helper = KeyStoreHelperFactory.getKeyStoreHelper()
                ChunkingDecryptingInputStream(bufferedReader, masterKey) { encrypted, key ->
                    helper.decrypterProviderWithKey(encrypted, key)
                }
            } else {
                // Legacy format: line4 is ivBackupLine, line5 is cipherBackupLine
                val ivBackup = line4.hexToByteArray()
                val cipherMasterLine2 = bufferedReader.readLine() ?: throw IllegalArgumentException("Missing backup data ciphertext")
                val cipherBackup = cipherMasterLine2.hexToByteArray()
                
                val helper = KeyStoreHelperFactory.getKeyStoreHelper()
                val decrypted = helper.decrypterProviderWithKey(
                    IVCipherText(ivBackup, cipherBackup),
                    masterKey
                )
                java.io.ByteArrayInputStream(decrypted)
            }

            myParser.setInput(xmlInputStream, null)

            reportProgress(null, null, "Process backup")
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
            passwordLogin(context, userPassword)
            reportProgress(null, null, "Finished with backup")
            return passwords
        } catch (ex: Exception) {
            Logger.e(TAG, "Restoration failed!", ex)
            ex.printStackTrace()
            firebaseRecordException("Failed to restore", ex)
            db.endTransaction()
            reportProgress(null, null, "Something failed, rollback")
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


    private fun parseXML(
        dbHelper: DBHelper,
        db: DBTransaction,
        myParser: XmlPullParser,
        lastBackupDone: Instant?,
        linkSaveGPMAndSiteEntry: (DBID, DBID) -> Unit,
        addSavedGPM: (SavedGPM) -> Unit,
        verifyOldBackupRestoration: (backupCreated: Instant, lastBackupDone: Instant) -> Boolean,
        reportProgress: (categories: Int?, passwords: Int?, message: String?) -> Unit,
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
                                    siteEntry!!.id = it
                                }
                            myParser.getTrimmedAttributeValue(Attributes.SITE_ENTRY_DELETED)
                                .toLongOrNull()?.let {
                                    siteEntry!!.deleted = it
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
                                    gpmLinkedToDeletedSiteEntries.forEach { gpmId, deletedSiteEntryIds ->
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

private class ChunkingDecryptingInputStream(
    private val reader: java.io.BufferedReader,
    private val masterKey: fi.iki.ede.crypto.keystore.KMPKey,
    private val decrypt: (IVCipherText, fi.iki.ede.crypto.keystore.KMPKey) -> ByteArray
) : java.io.InputStream() {
    private var buffer = byteArrayOf()
    private var index = 0
    private var EOF = false

    override fun read(): Int {
        if (index >= buffer.size) {
            if (EOF) return -1
            if (!fillBuffer()) return -1
        }
        return buffer[index++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var totalRead = 0
        while (totalRead < len) {
            if (index >= buffer.size) {
                if (EOF) {
                    return if (totalRead > 0) totalRead else -1
                }
                if (!fillBuffer()) {
                    return if (totalRead > 0) totalRead else -1
                }
            }
            val available = buffer.size - index
            val toWrite = minOf(available, len - totalRead)
            System.arraycopy(buffer, index, b, off + totalRead, toWrite)
            index += toWrite
            totalRead += toWrite
        }
        return totalRead
    }

    private fun fillBuffer(): Boolean {
        while (true) {
            val line = reader.readLine()
            if (line == null) {
                EOF = true
                return false
            }
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(':')
            if (parts.size != 2) {
                throw java.io.IOException("Malformed chunk line in backup file")
            }
            val iv = parts[0].hexToByteArray()
            val cipherText = parts[1].hexToByteArray()
            buffer = decrypt(IVCipherText(iv, cipherText), masterKey)
            index = 0
            return true
        }
    }

    override fun close() {
        reader.close()
    }
}