package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.IVCipherText

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
) {
    val decryptedName: String by lazy { encryptedName.decrypt() } // ok
    val decryptedUsername: String by lazy { encryptedUsername.decrypt() } // ok
    val decryptedUrl: String by lazy { encryptedUrl.decrypt() } // ok

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

    override fun toString(): String {
        return "SavedGPM ( id=$id, name=${decryptedName}, url=${decryptedUrl}, username=${decryptedUsername}, password=${encryptedPassword.decrypt()}, note=${encryptedNote.decrypt()}, flaggedIgnored=$flaggedIgnored, hash=$hash)"
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
