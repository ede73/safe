package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.DisallowedFunctions

/**
 * Convenience class representing salted password.
 *
 * DisallowedFunctions try to limit unintentional exposure
 *
 * Avoid using, prefer SaltedEncryptedPassword
 */
open class SaltedPassword(val salt: Salt, open val password: Password) : DisallowedFunctions() {
    open fun isEmpty() = salt.isEmpty() || password.isEmpty()

    companion object {
        fun getEmpty(): SaltedPassword =
            SaltedPassword(Salt.getEmpty(), Password.getEmpty())
    }
}