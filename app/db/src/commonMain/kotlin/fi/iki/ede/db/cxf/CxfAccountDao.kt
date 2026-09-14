package fi.iki.ede.db.cxf

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CxfAccountDao {
    @Query("SELECT * FROM cxf_accounts")
    suspend fun getAll(): List<CXFAccount>

    @Query("SELECT * FROM cxf_accounts WHERE cxf_account_id = :cxfAccountId LIMIT 1")
    suspend fun getByCxfAccountId(cxfAccountId: String): CXFAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: CXFAccount): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<CXFAccount>): List<Long>

    @Update
    suspend fun update(account: CXFAccount)

    @Delete
    suspend fun delete(account: CXFAccount)

    @Query("DELETE FROM cxf_accounts")
    suspend fun deleteAll()
}
