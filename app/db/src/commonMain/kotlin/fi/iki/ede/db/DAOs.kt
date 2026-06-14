package fi.iki.ede.db

import androidx.room.*
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM
import kotlin.time.ExperimentalTime

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: DecryptableCategoryEntry): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<DecryptableCategoryEntry>

    @Update
    suspend fun update(category: DecryptableCategoryEntry): Int
}

@OptIn(ExperimentalTime::class)
@Dao
interface SiteEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(siteEntry: DecryptableSiteEntry): Long

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM passwords WHERE category = :categoryId AND deleted = 0")
    suspend fun getByCategory(categoryId: Long): List<DecryptableSiteEntry>

    @Query("SELECT * FROM passwords WHERE category = :categoryId AND deleted != 0")
    suspend fun getByCategorySoftDeleted(categoryId: Long): List<DecryptableSiteEntry>

    @Query("SELECT * FROM passwords WHERE deleted = 0")
    suspend fun getAllActive(): List<DecryptableSiteEntry>

    @Query("SELECT * FROM passwords WHERE deleted != 0")
    suspend fun getAllSoftDeleted(): List<DecryptableSiteEntry>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DecryptableSiteEntry?

    @Query("SELECT photo FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getPhotoFilenameById(id: Long): String?

    @Update
    suspend fun update(siteEntry: DecryptableSiteEntry): Int

    @Query("UPDATE passwords SET category = :newCategoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, newCategoryId: Long): Int

    @Query("UPDATE passwords SET deleted = :deleted WHERE id = :id")
    suspend fun updateDeletedStatus(id: Long, deleted: Long): Int
}

@Dao
interface KeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: KeyEntry)

    @Query("SELECT * FROM keys LIMIT 1")
    suspend fun getFirst(): KeyEntry?

    @Query("DELETE FROM keys")
    suspend fun clear()
}

@Dao
interface GpmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gpm: SavedGPM): Long

    @Delete
    suspend fun delete(gpm: SavedGPM): Int

    @Query("DELETE FROM googlepasswords WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM googlepasswords")
    suspend fun deleteAll()

    @Update
    suspend fun update(gpm: SavedGPM): Int

    @Query("SELECT * FROM googlepasswords")
    suspend fun getAll(): List<SavedGPM>

    @Query("UPDATE googlepasswords SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int): Int
}

@Dao
interface SiteEntryGPMJoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(join: SiteEntryGPMJoin)

    @Query("SELECT * FROM password2googlepasswords")
    suspend fun getAll(): List<SiteEntryGPMJoin>

    @Query("DELETE FROM password2googlepasswords WHERE password_id = :passwordId AND gpm_id = :gpmId")
    suspend fun delete(passwordId: Long, gpmId: Long): Int

    @Query("DELETE FROM password2googlepasswords")
    suspend fun deleteAll(): Int
}
