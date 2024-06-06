package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing encrypted password.
 *
 * DisallowedFunctions try to limit unintentional exposure
 *
 * Prefer using SaltedEncryptedPassword
 */
class EncryptedPassword(val ivCipherText: IVCipherText) : DisallowedFunctions() {
    fun isEmpty(): Boolean = ivCipherText.isEmpty()
    override fun hashCode() = ivCipherText.hashCode()

    override fun equals(other: Any?): Boolean =
        (other != null) && (this.hashCode() == (other as? EncryptedPassword)?.hashCode())
    
    companion object {
        fun getEmpty() = EncryptedPassword(IVCipherText.getEmpty())
    }
}