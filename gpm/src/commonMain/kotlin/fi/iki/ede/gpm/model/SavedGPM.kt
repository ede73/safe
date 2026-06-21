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
    val cachedDecryptedName: String by lazy { encryptedName.decrypt() } // ok
    val cachedDecryptedUsername: String by lazy { encryptedUsername.decrypt() } // ok
    val cachedDecryptedUrl: String by lazy { encryptedUrl.decrypt() } // ok
    val cachedDecryptedPassword: String by lazy { encryptedPassword.decrypt() } // ok
    val cachedDecryptedNote: String by lazy { encryptedNote.decrypt() } // ok
    val harmonizedName: LowerCaseTrimmedString by lazy {
        harmonizePotentialDomainName(cachedDecryptedName).toLowerCasedTrimmedString()
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
