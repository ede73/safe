package fi.iki.ede.safe.model

import android.content.Context
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.db.DBHelperFactory

object LoginHandler {
    private var loggedIn: Boolean = false

    fun firstTimeLogin(context: Context, password: Password) {
        // this instance has no password (no exported master key!)
        val (salt, cipheredKey) = KeyStoreHelper.createNewKey(password)
        DBHelperFactory.getDBHelper(context).storeSaltAndEncryptedMasterKey(salt, cipheredKey)
        loggedIn = true
    }

    fun passwordLogin(context: Context, password: Password): Boolean {
        val (salt, cipheredMasterKey) =
            DBHelperFactory.getDBHelper(context).fetchSaltAndEncryptedMasterKey()
        return try {
            KeyStoreHelper.importExistingEncryptedMasterKey(
                SaltedPassword(salt, password),
                cipheredMasterKey
            )
            loggedIn = true
            true
        } catch (ex: Exception) {
            // TODO: Toast..
            false
        }
    }

    fun biometricLogin() {
        // Alas biometrics is just signal
        loggedIn = true
    }

    fun logout() {
        loggedIn = false
    }

    fun isLoggedIn() = loggedIn
}