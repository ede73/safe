package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing encrypted password.
 *
 * DisallowedFunctions try to limit unintentional exposure
 *
 * Prefer using SaltedEncryptedPassword
 */
data class EncryptedPassword(val ivCipherText: IVCipherText) : DisallowedFunctions() {
    fun isEmpty(): Boolean = ivCipherText.isEmpty()

    companion object {
        fun getEmpty() = EncryptedPassword(IVCipherText.getEmpty())
    }
}