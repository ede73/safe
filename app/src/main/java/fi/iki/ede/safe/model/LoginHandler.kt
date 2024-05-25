package fi.iki.ede.safe.model

import android.content.Context
import android.widget.Toast
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.R
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
            try {
                Toast.makeText(
                    context, context.getString(R.string.login_invalid_password),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (ex: Exception) {
                // TODO: unit test context dies on resource fetch

            }
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