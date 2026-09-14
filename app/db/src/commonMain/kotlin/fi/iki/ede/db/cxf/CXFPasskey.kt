package fi.iki.ede.db.cxf

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import kotlin.time.Clock

@Entity(
    tableName = "cxf_passkeys",
    foreignKeys = [
        ForeignKey(
            entity = CXFAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["cxf_item_id"])
    ]
)
data class CXFPasskey(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "cxf_item_id")
    val cxfItemId: String,
    @ColumnInfo(name = "cxf_account_id")
    val cxfAccountId: String,
    @ColumnInfo(name = "rp_id")
    val rpId: String = "",
    @ColumnInfo(name = "name")
    val encryptedName: IVCipherText,
    @ColumnInfo(name = "url")
    val encryptedUrl: IVCipherText,
    @ColumnInfo(name = "username")
    val encryptedUsername: IVCipherText,
    @ColumnInfo(name = "credential_id")
    val encryptedCredentialId: IVCipherText,
    @ColumnInfo(name = "user_handle")
    val encryptedUserHandle: IVCipherText,
    @ColumnInfo(name = "raw_credential_json")
    val encryptedRawCredentialJson: IVCipherText,
    @ColumnInfo(name = "note")
    val encryptedNote: IVCipherText,
    @ColumnInfo(name = "created_at")
    val createdAt: Long? = null,
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long? = null,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = Clock.System.now().toEpochMilliseconds(),
    @ColumnInfo(name = "flagged_ignored")
    val flaggedIgnored: Boolean = false,
    @ColumnInfo(name = "hash")
    val hash: String
) : DisallowedFunctions {

    @Ignore
    internal var _cachedDecryptedName: String? = null
    @Ignore
    internal var _cachedDecryptedUsername: String? = null
    @Ignore
    internal var _cachedDecryptedUrl: String? = null
    @Ignore
    internal var _cachedDecryptedCredentialId: String? = null
    @Ignore
    internal var _cachedDecryptedUserHandle: String? = null
    @Ignore
    internal var _cachedDecryptedRawCredentialJson: String? = null
    @Ignore
    internal var _cachedDecryptedNote: String? = null

    @Ignore
    constructor(
        id: Long? = null,
        accountId: Long,
        cxfItemId: String,
        cxfAccountId: String,
        rpId: String = "",
        name: String,
        url: String,
        username: String,
        credentialId: String = "",
        userHandle: String = "",
        rawCredentialJson: String,
        note: String,
        createdAt: Long? = null,
        modifiedAt: Long? = null,
        importedAt: Long = Clock.System.now().toEpochMilliseconds(),
        flaggedIgnored: Boolean = false,
        hash: String
    ) : this(
        id = id,
        accountId = accountId,
        cxfItemId = cxfItemId,
        cxfAccountId = cxfAccountId,
        rpId = rpId,
        encryptedName = name.encrypt(),
        encryptedUrl = url.encrypt(),
        encryptedUsername = username.encrypt(),
        encryptedCredentialId = credentialId.encrypt(),
        encryptedUserHandle = userHandle.encrypt(),
        encryptedRawCredentialJson = rawCredentialJson.encrypt(),
        encryptedNote = note.encrypt(),
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        importedAt = importedAt,
        flaggedIgnored = flaggedIgnored,
        hash = hash
    )
}

val CXFPasskey.cachedDecryptedName: String
    get() = _cachedDecryptedName ?: encryptedName.decrypt().also { _cachedDecryptedName = it }

val CXFPasskey.cachedDecryptedUsername: String
    get() = _cachedDecryptedUsername ?: encryptedUsername.decrypt().also { _cachedDecryptedUsername = it }

val CXFPasskey.cachedDecryptedUrl: String
    get() = _cachedDecryptedUrl ?: encryptedUrl.decrypt().also { _cachedDecryptedUrl = it }

val CXFPasskey.cachedDecryptedCredentialId: String
    get() = _cachedDecryptedCredentialId ?: encryptedCredentialId.decrypt().also { _cachedDecryptedCredentialId = it }

val CXFPasskey.cachedDecryptedUserHandle: String
    get() = _cachedDecryptedUserHandle ?: encryptedUserHandle.decrypt().also { _cachedDecryptedUserHandle = it }

val CXFPasskey.cachedDecryptedRawCredentialJson: String
    get() = _cachedDecryptedRawCredentialJson ?: encryptedRawCredentialJson.decrypt().also { _cachedDecryptedRawCredentialJson = it }

val CXFPasskey.cachedDecryptedNote: String
    get() = _cachedDecryptedNote ?: encryptedNote.decrypt().also { _cachedDecryptedNote = it }
