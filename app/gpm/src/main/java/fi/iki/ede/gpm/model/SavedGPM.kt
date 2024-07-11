package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.DisallowedFunctions
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString

data class SavedGPM private constructor(
    val id: Long? = null,
    // often decrypted, decryptedName to cache
    val encryptedName: IVCipherText,
    // often decrypted, decryptedUrl to cache
    val encryptedUrl: IVCipherText,
    // often decrypted, decryptedUsername to cache
    val encryptedUsername: IVCipherText,
    val encryptedPassword: IVCipherText,
    val encryptedNote: IVCipherText,
    val flaggedIgnored: Boolean,
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
