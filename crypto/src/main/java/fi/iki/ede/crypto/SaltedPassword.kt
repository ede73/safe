package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing salted password.
 *
 * DisallowedFunctions try to limit unintentional exposure
 *
 * Avoid using, prefer SaltedEncryptedPassword
 */
data class SaltedPassword(val salt: Salt, val password: Password) : DisallowedFunctions {
    fun isEmpty() = salt.isEmpty() || password.isEmpty()

    companion object {
        fun getEmpty(): SaltedPassword =
            SaltedPassword(Salt.getEmpty(), Password.getEmpty())
    }
}