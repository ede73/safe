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
    var cachedDecryptedName: String = ""
        get() {
            if (field.isEmpty() && encryptedName.isNotEmpty()) {
                field = encryptedName.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var cachedDecryptedUsername: String = ""
        get() {
            if (field.isEmpty() && encryptedUsername.isNotEmpty()) {
                field = encryptedUsername.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var cachedDecryptedUrl: String = ""
        get() {
            if (field.isEmpty() && encryptedUrl.isNotEmpty()) {
                field = encryptedUrl.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var cachedDecryptedPassword: String = ""
        get() {
            if (field.isEmpty() && encryptedPassword.isNotEmpty()) {
                field = encryptedPassword.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var cachedDecryptedNote: String = ""
        get() {
            if (field.isEmpty() && encryptedNote.isNotEmpty()) {
                field = encryptedNote.decrypt()
            }
            return field
        }
        set(value) {
            field = value
        }
    @Ignore
    var harmonizedName: LowerCaseTrimmedString = "".toLowerCasedTrimmedString()
        get() {
            if (field.lowercasedTrimmed.isEmpty() && cachedDecryptedName.isNotEmpty()) {
                field = harmonizePotentialDomainName(cachedDecryptedName).toLowerCasedTrimmedString()
            }
            return field
        }
        set(value) {
            field = value
        }

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
        return "SavedGPM ( id=$id, name=${cachedDecryptedName}, url=${cachedDecryptedUrl}, username=${cachedDecryptedUsername}, password=REDACTED, note=${encryptedNote.decrypt()}, flaggedIgnored=$flaggedIgnored, hash=$hash)"
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
//                calculateSha128(
//                    listOf(
//                        encryptedName.decrypt(), // ok
//                        encryptedUrl.decrypt(), // ok
//                        encryptedUsername.decrypt(), // ok
//                        encryptedPassword.decrypt(), // ok
//                        encryptedNote.decrypt() // ok
//                    ),
//                    "makeFromEncryptedStringFields"
//                )
            )
    }
}
