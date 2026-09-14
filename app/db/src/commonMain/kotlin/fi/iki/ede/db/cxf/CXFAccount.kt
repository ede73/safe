package fi.iki.ede.db.cxf

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import fi.iki.ede.crypto.support.DisallowedFunctions

@Entity(tableName = "cxf_accounts")
data class CXFAccount(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "cxf_account_id")
    val cxfAccountId: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis()
) : DisallowedFunctions
