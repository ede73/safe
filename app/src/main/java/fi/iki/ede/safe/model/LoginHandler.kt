package fi.iki.ede.safe.model

import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.notifications.showToast
import fi.iki.ede.safe.R
import kotlin.time.ExperimentalTime

object LoginHandler {
    private var loggedIn: Boolean = false

    fun biometricLogin() {
        // Alas biometrics is just signal
        loggedIn = true
    }

    @ExperimentalTime
    fun firstTimeLogin(password: Password) {
        // this instance has no password (no exported master key!)
        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(password)
        // TODO: async
        DBHelperFactory.getDBHelper().storeSaltAndEncryptedMasterKey(salt, cipheredKey)
        loggedIn = true
    }

    fun isLoggedIn() = loggedIn

    fun logout() {
        loggedIn = false
    }

    @ExperimentalTime
    fun passwordLogin(password: Password): Boolean {
        // TODO: async
        val (salt, cipheredMasterKey) =
            DBHelperFactory.getDBHelper().fetchSaltAndEncryptedMasterKey()
        return try {
            KeyStoreHelper.importExistingEncryptedMasterKey(
                SaltedPassword(salt, password),
                cipheredMasterKey
            )
            loggedIn = true
            true
        } catch (ex: Exception) {
            try {
                showToast(R.string.login_invalid_password)
            } catch (ex: Exception) {
                // TODO: unit test context dies on resource fetch
            }
            false
        }
    }
}