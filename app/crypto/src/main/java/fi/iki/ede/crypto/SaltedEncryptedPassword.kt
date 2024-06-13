package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing salted encrypted password.
 *
 * DisallowedFunctions try to limit unintentional exposure
 */
data class SaltedEncryptedPassword(val salt: Salt, val encryptedPassword: EncryptedPassword) :
    DisallowedFunctions() {
    fun isEmpty() = salt.isEmpty() || encryptedPassword.isEmpty()

    companion object {
        fun getEmpty(): SaltedEncryptedPassword =
            SaltedEncryptedPassword(Salt.getEmpty(), EncryptedPassword.getEmpty())
    }
}