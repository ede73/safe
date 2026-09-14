package fi.iki.ede.db.cxf

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CxfPasskeyDao {
    @Query("SELECT * FROM cxf_passkeys")
    suspend fun getAll(): List<CXFPasskey>

    @Query("SELECT * FROM cxf_passkeys WHERE account_id = :accountId")
    suspend fun getByAccountId(accountId: Long): List<CXFPasskey>

    @Query("SELECT * FROM cxf_passkeys WHERE cxf_item_id = :cxfItemId LIMIT 1")
    suspend fun getByCxfItemId(cxfItemId: String): CXFPasskey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passkey: CXFPasskey): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(passkeys: List<CXFPasskey>): List<Long>

    @Update
    suspend fun update(passkey: CXFPasskey)

    @Delete
    suspend fun delete(passkey: CXFPasskey)

    @Query("DELETE FROM cxf_passkeys")
    suspend fun deleteAll()

    @Query("UPDATE cxf_passkeys SET flagged_ignored = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Boolean): Int
}
