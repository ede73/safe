package fi.iki.ede.db.cxf

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import kotlin.time.Clock

@Entity(tableName = "cxf_accounts")
data class CXFAccount(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "cxf_account_id")
    val cxfAccountId: String,
    @ColumnInfo(name = "email")
    val encryptedEmail: IVCipherText,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = Clock.System.now().toEpochMilliseconds()
) : DisallowedFunctions {

    @Ignore
    internal var _cachedDecryptedEmail: String? = null

    @Ignore
    constructor(
        id: Long? = null,
        cxfAccountId: String,
        email: String,
        importedAt: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        id = id,
        cxfAccountId = cxfAccountId,
        encryptedEmail = email.encrypt(),
        importedAt = importedAt
    )
}

val CXFAccount.cachedDecryptedEmail: String
    get() = _cachedDecryptedEmail ?: encryptedEmail.decrypt().also { _cachedDecryptedEmail = it }

