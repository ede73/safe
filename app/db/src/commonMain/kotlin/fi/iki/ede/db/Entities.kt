package fi.iki.ede.db

import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "keys", primaryKeys = ["salt", "encryptedkey"])
data class KeyEntry(
    @ColumnInfo(name = "encryptedkey")
    val encryptedKey: ByteArray,
    @ColumnInfo(name = "salt")
    val salt: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KeyEntry
        if (!encryptedKey.contentEquals(other.encryptedKey)) return false
        if (!salt.contentEquals(other.salt)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = encryptedKey.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }
}

@Entity(tableName = "password2googlepasswords", primaryKeys = ["password_id", "gpm_id"])
data class SiteEntryGPMJoin(
    @ColumnInfo(name = "password_id")
    val passwordId: Long,
    @ColumnInfo(name = "gpm_id")
    val gpmId: Long
)
