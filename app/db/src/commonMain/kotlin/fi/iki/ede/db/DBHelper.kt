package fi.iki.ede.db

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
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
    val context: Any? = null,
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
    private val photoDir: Path = getPhotoDir(context)
    var skipPrepopulate: Boolean = false

    init {
        initTpmKeys()
        val builder = if (databaseName == null) {
            getInMemoryDatabaseBuilder(context)
        } else {
            getDatabaseBuilder(context)
        }
        database = builder.build()

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

    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) = runBlocking {
        database.keyDao().clear()
        database.keyDao().insert(
            KeyEntry(
                encryptedKey = ivCipher.combineIVAndCipherText(),
                salt = salt.salt
            )
        )
    }

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
        val categories = database.categoryDao().getAll()
        categories.forEach { category ->
            category.containedSiteEntryCount = database.siteEntryDao().getByCategory(category.id!!).size
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
        val list = if (categoryId != null) {
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
        if (siteEntriesFlow != null) {
            siteEntriesFlow.value = list
        }
        list
    }

    fun updateSiteEntry(entry: DecryptableSiteEntry): DBID = runBlocking {
        require(entry.id != null) { "Cannot update SiteEntry without ID" }
        require(entry.categoryId != null) { "Cannot update SiteEntry without Category ID" }
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

    fun markSiteEntryDeleted(id: DBID): Int = runBlocking {
        database.siteEntryDao().updateDeletedStatus(id, Random.nextLong(1, Long.MAX_VALUE))
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
        }
    }
}
