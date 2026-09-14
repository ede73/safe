package fi.iki.ede.db.cxf

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CxfImportDao {
    @Query("SELECT * FROM cxf_imports")
    suspend fun getAll(): List<CXFImport>

    @Query("SELECT * FROM cxf_imports WHERE account_id = :accountId")
    suspend fun getByAccountId(accountId: Long): List<CXFImport>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(import: CXFImport): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(imports: List<CXFImport>): List<Long>

    @Update
    suspend fun update(import: CXFImport)

    @Delete
    suspend fun delete(import: CXFImport)

    @Query("DELETE FROM cxf_imports")
    suspend fun deleteAll()

    @Query("UPDATE cxf_imports SET flagged_ignored = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Boolean): Int
}
