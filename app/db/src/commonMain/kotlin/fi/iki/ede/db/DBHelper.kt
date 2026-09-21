package fi.iki.ede.db

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.time.ExperimentalTime

typealias DBID = Long
typealias FileName = String

interface DBTransaction {
    fun setTransactionSuccessful()
    fun endTransaction()
}

@ExperimentalTime
class DBHelper(
    val databaseName: String? = "safe",
    val regularAppNotATest: Boolean = false,
    val getExternalTables: Any? = null,
    val upgradeExternalTables: Any? = null
) {
    companion object {
        const val TAG = "DBHelper"
        const val DATABASE_NAME = "safe"
    }

    val database: SafeDatabase
    private val photoDir: Path = getPhotoDir()
    var skipPrepopulate: Boolean = false

    init {
        initTpmKeys()
        val builder = if (databaseName == null) {
            getInMemoryDatabaseBuilder()
        } else {
            getDatabaseBuilder(databaseName)
        }
        database = builder
            .addMigrations(MIGRATION_7_9, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()

        if (!FileSystem.SYSTEM.exists(photoDir) && runCatching {
                FileSystem.SYSTEM.createDirectories(photoDir)
            }.isFailure) {
            Logger.e(TAG, "FAILED MAKING PHOTO DIR")
        }
    }

    fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {
        fi.iki.ede.db.storeTpmKeys(privateKeyBase64, publicKeyBase64)
    }

    fun fetchTpmKeys(): Pair<String, String>? {
        return fi.iki.ede.db.fetchTpmKeys()
    }

    @Suppress("DEPRECATION")
    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) = runBlocking {
        database.keyDao().clear()
        database.keyDao().insert(
            KeyEntry(
                encryptedKey = ivCipher.combineIVAndCipherText(),
                salt = salt.salt
            )
        )
    }

    @Suppress("DEPRECATION")
    fun fetchSaltAndEncryptedMasterKey(): Pair<Salt, IVCipherText> = runBlocking {
        val key = database.keyDao().getFirst() ?: throw Exception("No master key")
        Pair(Salt(key.salt), IVCipherText(16, key.encryptedKey))
    }

    fun addCategory(entry: DecryptableCategoryEntry): DBID = runBlocking {
        database.categoryDao().insert(entry)
    }

    fun deleteCategory(id: DBID): Int = runBlocking {
        // Cascade delete passwords in category
        database.siteEntryDao().getByCategory(id).forEach {
            database.siteEntryDao().deleteById(it.id!!)
        }
        database.siteEntryDao().getByCategorySoftDeleted(id).forEach {
            database.siteEntryDao().deleteById(it.id!!)
        }
        database.categoryDao().deleteById(id)
    }

    fun fetchAllCategoryRows(categoriesFlow: MutableStateFlow<List<DecryptableCategoryEntry>>? = null): List<DecryptableCategoryEntry> = runBlocking {
        val categories = database.categoryDao().getAll().toMutableList()
        val cxfAccounts = database.cxfAccountDao().getAll()
        val cxfImports = database.cxfImportDao().getAll()
        val cxfPasskeys = database.cxfPasskeyDao().getAll()

        for (account in cxfAccounts) {
            val accountId = account.id ?: continue
            val syntheticCat = fi.iki.ede.db.cxf.CxfSyntheticModelMapper.toSyntheticCategory(account)
            val importCount = cxfImports.count { it.accountId == accountId }
            val passkeyCount = cxfPasskeys.count { it.accountId == accountId }
            syntheticCat.containedSiteEntryCount = importCount + passkeyCount
            categories.add(syntheticCat)
        }

        categories.forEach { category ->
            if (!fi.iki.ede.db.cxf.CxfSyntheticModelMapper.isSyntheticId(category.id)) {
                category.containedSiteEntryCount = database.siteEntryDao().getByCategory(category.id!!).size
            }
        }
        if (categoriesFlow != null) {
            categoriesFlow.value = categories
        }
        categories
    }

    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry): Long = runBlocking {
        entry.id = id
        database.categoryDao().update(entry)
        id
    }

    fun fetchPhotoOnly(siteEntryID: DBID): IVCipherText? = runBlocking {
        val filename = database.siteEntryDao().getPhotoFilenameById(siteEntryID)
        if (filename.isNullOrEmpty()) null else loadPhoto(filename)
    }

    fun fetchAllRows(
        categoryId: DBID? = null,
        softDeletedOnly: Boolean = false,
        siteEntriesFlow: MutableStateFlow<List<DecryptableSiteEntry>>? = null
    ): List<DecryptableSiteEntry> = runBlocking {
        val list = mutableListOf<DecryptableSiteEntry>()
        if (categoryId != null && fi.iki.ede.db.cxf.CxfSyntheticModelMapper.isSyntheticId(categoryId)) {
            val cxfAccountId = fi.iki.ede.db.cxf.DecryptableCXFCategoryEntry.CATEGORY_ID_OFFSET - categoryId
            if (!softDeletedOnly && cxfAccountId > 0L) {
                val imports = database.cxfImportDao().getByAccountId(cxfAccountId)
                val passkeys = database.cxfPasskeyDao().getByAccountId(cxfAccountId)
                list.addAll(imports.map { fi.iki.ede.db.cxf.CxfSyntheticModelMapper.toSyntheticSiteEntry(it, cxfAccountId) })
                list.addAll(passkeys.map { fi.iki.ede.db.cxf.CxfSyntheticModelMapper.toSyntheticSiteEntry(it, cxfAccountId) })
            }
        } else {
            val dbList = if (categoryId != null) {
                if (softDeletedOnly) {
                    database.siteEntryDao().getByCategorySoftDeleted(categoryId)
                } else {
                    database.siteEntryDao().getByCategory(categoryId)
                }
            } else {
                if (softDeletedOnly) {
                    database.siteEntryDao().getAllSoftDeleted()
                } else {
                    database.siteEntryDao().getAllActive()
                }
            }
            list.addAll(dbList)
            if (categoryId == null && !softDeletedOnly) {
                val cxfAccounts = database.cxfAccountDao().getAll()
                for (account in cxfAccounts) {
                    val accountId = account.id ?: continue
                    val imports = database.cxfImportDao().getByAccountId(accountId)
                    val passkeys = database.cxfPasskeyDao().getByAccountId(accountId)
                    list.addAll(imports.map { fi.iki.ede.db.cxf.CxfSyntheticModelMapper.toSyntheticSiteEntry(it, accountId) })
                    list.addAll(passkeys.map { fi.iki.ede.db.cxf.CxfSyntheticModelMapper.toSyntheticSiteEntry(it, accountId) })
                }
            }
        }
        val sorted = list.sortedBy { it.plainDescription.lowercase() }
        if (siteEntriesFlow != null) {
            siteEntriesFlow.value = sorted
        }
        sorted
    }

    fun updateSiteEntry(entry: DecryptableSiteEntry): DBID = runBlocking {
        if (entry is fi.iki.ede.db.cxf.DecryptableCXFSiteEntry) {
            if (entry.cxfImport != null) {
                val updated = entry.cxfImport.copy(
                    encryptedName = entry.description,
                    encryptedUsername = entry.username,
                    encryptedPassword = entry.password,
                    encryptedUrl = entry.website,
                    encryptedNote = entry.note,
                    modifiedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                )
                database.cxfImportDao().update(updated)
                return@runBlocking entry.id!!
            } else if (entry.cxfPasskey != null) {
                val updated = entry.cxfPasskey.copy(
                    encryptedName = entry.description,
                    encryptedUsername = entry.username,
                    encryptedUrl = entry.website,
                    encryptedNote = entry.note,
                    modifiedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                )
                database.cxfPasskeyDao().update(updated)
                return@runBlocking entry.id!!
            }
        }
        require(entry.id != null) { "Cannot update SiteEntry without ID" }
        database.siteEntryDao().getPhotoFilenameById(entry.id!!)?.let { deletePhoto(it) }
        entry.photoFilename = savePhoto(entry.photo)
        database.siteEntryDao().update(entry)
        entry.id!!
    }

    fun updateSiteEntryCategory(id: DBID, newCategoryId: DBID): Int = runBlocking {
        database.siteEntryDao().updateCategory(id, newCategoryId)
    }

    fun addSiteEntry(entry: DecryptableSiteEntry): Long = runBlocking {
        entry.photoFilename = savePhoto(entry.photo)
        database.siteEntryDao().insert(entry)
    }

    fun fetchPhotoFilename(siteEntryID: DBID): FileName? = runBlocking {
        database.siteEntryDao().getPhotoFilenameById(siteEntryID)
    }

    @Suppress("DEPRECATION")
    fun loadPhoto(photoName: FileName): IVCipherText? = (photoDir / photoName).let { path ->
        if (!FileSystem.SYSTEM.exists(path)) null
        else IVCipherText(
            16,
            FileSystem.SYSTEM.read(path) { readByteArray() }
        )
    }

    fun deletePhoto(photoName: FileName) {
        val path = photoDir / photoName
        if (FileSystem.SYSTEM.exists(path)) {
            FileSystem.SYSTEM.delete(path)
        }
    }

    fun savePhoto(photo: IVCipherText): FileName? {
        if (photo.isEmpty()) return null
        val path = photoDir / "%016x%016x.photo_data".format(
            Random.nextLong(),
            Random.nextLong()
        )
        return runCatching {
            FileSystem.SYSTEM.write(path) {
                write(photo.iv)
                write(photo.cipherText)
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error saving photo ${path.name}: ${e.message}", e)
            runCatching { if (FileSystem.SYSTEM.exists(path)) FileSystem.SYSTEM.delete(path) }
        }.getOrNull()?.let { path.name }
    }

    fun restoreSoftDeletedSiteEntry(id: DBID): Int = runBlocking {
        database.siteEntryDao().updateDeletedStatus(id, 0L)
    }

    fun markSiteEntryDeleted(id: DBID, deletedTimeSeconds: Long = fi.iki.ede.dateutils.DateUtils.toUnixSeconds()): Int = runBlocking {
        database.siteEntryDao().updateDeletedStatus(id, deletedTimeSeconds)
    }

    fun hardDeleteSiteEntry(id: DBID): Int = runBlocking {
        database.siteEntryDao().deleteById(id)
    }

    fun beginTransaction() = beginTransaction(database)
    fun setTransactionSuccessful() = setTransactionSuccessful(database)
    fun endTransaction() = endTransaction(database)

    fun beginRestoration(): DBTransaction {
        beginTransaction()
        runBlocking {
            try {
                database.categoryDao().getAll().forEach { database.categoryDao().deleteById(it.id!!) }
                database.siteEntryDao().getAllActive().forEach { database.siteEntryDao().deleteById(it.id!!) }
                database.siteEntryDao().getAllSoftDeleted().forEach { database.siteEntryDao().deleteById(it.id!!) }
                database.keyDao().clear()
                database.gpmDao().deleteAll()
                database.siteEntryGPMJoinDao().deleteAll()
                database.cxfAccountDao().deleteAll()
                database.cxfImportDao().deleteAll()
                database.cxfPasskeyDao().deleteAll()
            } catch (e: Exception) {
                endTransaction()
                throw e
            }
        }
        return object : DBTransaction {
            override fun setTransactionSuccessful() {
                this@DBHelper.setTransactionSuccessful()
            }
            override fun endTransaction() {
                this@DBHelper.endTransaction()
            }
        }
    }

    fun clearAllData() {
        runBlocking {
            database.categoryDao().getAll().forEach { database.categoryDao().deleteById(it.id!!) }
            database.siteEntryDao().getAllActive().forEach { database.siteEntryDao().deleteById(it.id!!) }
            database.siteEntryDao().getAllSoftDeleted().forEach { database.siteEntryDao().deleteById(it.id!!) }
            database.gpmDao().deleteAll()
            database.siteEntryGPMJoinDao().deleteAll()
            database.cxfAccountDao().deleteAll()
            database.cxfImportDao().deleteAll()
            database.cxfPasskeyDao().deleteAll()
        }
    }
}
