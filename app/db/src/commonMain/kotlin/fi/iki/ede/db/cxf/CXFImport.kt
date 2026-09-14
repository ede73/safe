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
    tableName = "cxf_imports",
    foreignKeys = [
        ForeignKey(
            entity = CXFAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"])
    ]
)
data class CXFImport(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "cxf_item_id")
    val encryptedCxfItemId: IVCipherText,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "name")
    val encryptedName: IVCipherText,
    @ColumnInfo(name = "url")
    val encryptedUrl: IVCipherText,
    @ColumnInfo(name = "username")
    val encryptedUsername: IVCipherText,
    @ColumnInfo(name = "password")
    val encryptedPassword: IVCipherText,
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
    internal var _cachedDecryptedCxfItemId: String? = null
    @Ignore
    internal var _cachedDecryptedName: String? = null
    @Ignore
    internal var _cachedDecryptedUsername: String? = null
    @Ignore
    internal var _cachedDecryptedUrl: String? = null
    @Ignore
    internal var _cachedDecryptedPassword: String? = null
    @Ignore
    internal var _cachedDecryptedNote: String? = null

    @Ignore
    constructor(
        id: Long? = null,
        accountId: Long,
        cxfItemId: String,
        type: String,
        name: String,
        url: String,
        username: String,
        password: String,
        note: String,
        createdAt: Long? = null,
        modifiedAt: Long? = null,
        importedAt: Long = Clock.System.now().toEpochMilliseconds(),
        flaggedIgnored: Boolean = false,
        hash: String
    ) : this(
        id = id,
        accountId = accountId,
        encryptedCxfItemId = cxfItemId.encrypt(),
        type = type,
        encryptedName = name.encrypt(),
        encryptedUrl = url.encrypt(),
        encryptedUsername = username.encrypt(),
        encryptedPassword = password.encrypt(),
        encryptedNote = note.encrypt(),
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        importedAt = importedAt,
        flaggedIgnored = flaggedIgnored,
        hash = hash
    )
}

val CXFImport.cachedDecryptedCxfItemId: String
    get() = _cachedDecryptedCxfItemId ?: encryptedCxfItemId.decrypt().also { _cachedDecryptedCxfItemId = it }

val CXFImport.cachedDecryptedName: String
    get() = _cachedDecryptedName ?: encryptedName.decrypt().also { _cachedDecryptedName = it }

val CXFImport.cachedDecryptedUsername: String
    get() = _cachedDecryptedUsername ?: encryptedUsername.decrypt().also { _cachedDecryptedUsername = it }

val CXFImport.cachedDecryptedUrl: String
    get() = _cachedDecryptedUrl ?: encryptedUrl.decrypt().also { _cachedDecryptedUrl = it }

val CXFImport.cachedDecryptedPassword: String
    get() = _cachedDecryptedPassword ?: encryptedPassword.decrypt().also { _cachedDecryptedPassword = it }

val CXFImport.cachedDecryptedNote: String
    get() = _cachedDecryptedNote ?: encryptedNote.decrypt().also { _cachedDecryptedNote = it }

