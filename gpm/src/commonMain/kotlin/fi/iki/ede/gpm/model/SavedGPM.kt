package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Ignore

@Entity(tableName = "googlepasswords")
data class SavedGPM(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long? = null,
    // often decrypted, decryptedName to cache
    @ColumnInfo(name = "name")
    val encryptedName: IVCipherText,
    // often decrypted, decryptedUrl to cache
    @ColumnInfo(name = "url")
    val encryptedUrl: IVCipherText,
    // often decrypted, decryptedUsername to cache
    @ColumnInfo(name = "username")
    val encryptedUsername: IVCipherText,
    @ColumnInfo(name = "password")
    val encryptedPassword: IVCipherText,
    @ColumnInfo(name = "note")
    val encryptedNote: IVCipherText,
    @ColumnInfo(name = "status")
    val flaggedIgnored: Boolean,
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
    internal var _cachedDecryptedPassword: String? = null
    @Ignore
    internal var _cachedDecryptedNote: String? = null
    @Ignore
    internal var _harmonizedName: LowerCaseTrimmedString? = null

    @Ignore
    constructor(id: Long? = null, importing: IncomingGPM) : this(
        id,
        importing.name.encrypt(),
        importing.url.encrypt(),
        importing.username.encrypt(),
        importing.password.encrypt(),
        importing.note.encrypt(),
        false,
        importing.hash // assert?check?
    )

    fun toStringRedacted(): String {
        return "SavedGPM ( id=$id, name=${cachedDecryptedName}, url=${cachedDecryptedUrl}, username=${cachedDecryptedUsername}, password=REDACTED, note=${cachedDecryptedNote}, flaggedIgnored=$flaggedIgnored, hash=$hash)"
    }

    companion object {
        fun makeFromEncryptedStringFields(
            id: Long? = null,
            encryptedName: IVCipherText,
            encryptedUrl: IVCipherText,
            encryptedUsername: IVCipherText,
            encryptedPassword: IVCipherText,
            encryptedNote: IVCipherText,
            flaggedIgnored: Boolean,
            hash: String,
        ): SavedGPM =
            SavedGPM(
                id,
                encryptedName,
                encryptedUrl,
                encryptedUsername,
                encryptedPassword,
                encryptedNote,
                flaggedIgnored,
                hash
            )
    }
}

val SavedGPM.cachedDecryptedName: String
    get() = _cachedDecryptedName ?: encryptedName.decrypt().also { _cachedDecryptedName = it }

val SavedGPM.cachedDecryptedUsername: String
    get() = _cachedDecryptedUsername ?: encryptedUsername.decrypt().also { _cachedDecryptedUsername = it }

val SavedGPM.cachedDecryptedUrl: String
    get() = _cachedDecryptedUrl ?: encryptedUrl.decrypt().also { _cachedDecryptedUrl = it }

val SavedGPM.cachedDecryptedPassword: String
    get() = _cachedDecryptedPassword ?: encryptedPassword.decrypt().also { _cachedDecryptedPassword = it }

val SavedGPM.cachedDecryptedNote: String
    get() = _cachedDecryptedNote ?: encryptedNote.decrypt().also { _cachedDecryptedNote = it }

val SavedGPM.harmonizedName: LowerCaseTrimmedString
    get() = _harmonizedName ?: harmonizePotentialDomainName(cachedDecryptedName).toLowerCasedTrimmedString().also { _harmonizedName = it }

val SavedGPM.plainName: String get() = cachedDecryptedName
val SavedGPM.plainUsername: String get() = cachedDecryptedUsername
val SavedGPM.plainUrl: String get() = cachedDecryptedUrl
val SavedGPM.plainPassword: String get() = cachedDecryptedPassword
val SavedGPM.plainNote: String get() = cachedDecryptedNote
