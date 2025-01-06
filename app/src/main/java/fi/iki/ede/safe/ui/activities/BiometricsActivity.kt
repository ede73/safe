package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.preferences.Preferences.PREFERENCE_BIOMETRICS_ENABLED
import fi.iki.ede.preferences.Preferences.PREFERENCE_BIO_CIPHER
import fi.iki.ede.preferences.Preferences.sharedPreferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.LoginHandler
import java.time.ZonedDateTime

// TODO: With latest jetpack biometric lib, authentication failed flow seems to have changed
// Previously there were couple of tries, not it flat out fails
// End result is login screen activates, but biometric screen still stays on, so user had to
// navigate back, then enter password or launch biometric again
// Not a big deal, but feels buggy
class BiometricsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is needed to keep background ..light or dark when biometric prompt pops up
        // (title of this activity still seems to be white, but what can you do)
        setContent {
            fi.iki.ede.theme.SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val title = when (intent.action) {
            BIO_VERIFY -> R.string.biometrics_authenticate_title
            BIO_INITIALIZE -> R.string.biometrics_register_title
            else -> R.string.biometrics_authenticate_title // never happens
        }
        val subtitle = when (intent.action) {
            BIO_VERIFY -> R.string.biometrics_authenticate_subtitle
            BIO_INITIALIZE -> R.string.biometrics_register_subtitle
            else -> R.string.biometrics_authenticate_subtitle // never happens
        }

        bioAuthenticateUser(getString(title), getString(subtitle))
    }

    /**
     * Run bioauth, should it fail (cant init, read failure what ever, fall back to password)
     */
    private fun bioAuthenticateUser(title: String, subtitle: String) {
        val biometricManager: BiometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt(title, subtitle)
                return
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> showMessage("ERROR: There is no suitable hardware")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> showMessage("ERROR: The hardware is unavailable. Try again later")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> showMessage("ERROR: No biometric or device credential is enrolled")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> showMessage("ERROR: A security vulnerability has been discovered with one or more hardware sensors")
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> showMessage("ERROR: The specified options are incompatible with the current Android version")
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> showMessage("ERROR: Unable to determine whether the user can authenticate")
        }
        setResult(RESULT_CANCELED, Intent().setAction(intent.action))
        finish()
    }

    private fun showBiometricPrompt(title: String, subtitle: String) {
        val promptInfo: PromptInfo = PromptInfo.Builder()
            .setTitle(getString(R.string.biometrics_prompt_title))
            .setSubtitle(title)
            .setDescription(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(getString(R.string.biometrics_cancel))
            .setConfirmationRequired(true)
            .build()
        val authCallback: BiometricPrompt.AuthenticationCallback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showMessage(
                        getString(
                            R.string.biometrics_authentication_error,
                            errorCode,
                            errString
                        )
                    )
                    setResult(RESULT_CANCELED, Intent().setAction(intent.action))
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showMessage(getString(R.string.biometrics_authentication_failed))
                    setResult(RESULT_FAILED, Intent().setAction(intent.action))
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setResult(RESULT_OK, Intent().setAction(intent.action))
                    finish()
                }
            }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), authCallback)
        prompt.authenticate(promptInfo)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "BiometricsActivity"

        fun isBiometricEnabled() =
            sharedPreferences.getBoolean(PREFERENCE_BIOMETRICS_ENABLED, false)

        fun setBiometricEnabled(value: Boolean) =
            sharedPreferences.edit().putBoolean(PREFERENCE_BIOMETRICS_ENABLED, value).apply()

        fun haveRecordedBiometric() = getBioCipher().isNotEmpty()

        /**
         * Call after biometrics initialize if RESULT_OK (ie. user's biometrics was recognized)
         * We shall store the masterkey to KeyStore (and use it for subsequent biometric
         * verifications)
         */
        fun registerBiometric() {
            // encrypt something funny with biokey
            val ks = KeyStoreHelperFactory.getKeyStoreHelper() // needed, new key
            val biokey = ks.getOrCreateBiokey()
            val now = ZonedDateTime.now().toEpochSecond().toString()
            val stamp = ks.encryptByteArray(now.toByteArray(), biokey)
            storeBioCipher(stamp)
            LoginHandler.biometricLogin()
        }

        /**
         * Call after successful biometric verification
         *
         * TODO: Implement some check - keystore has separate biokey in android keystore
         */
        fun verificationAccepted(): Boolean {
            val stampCipher = getBioCipher()
            val ks = KeyStoreHelperFactory.getKeyStoreHelper() // needed, new key
            val biometricKey = ks.getOrCreateBiokey()
            try {
                val decrypted = ks.decryptByteArray(stampCipher, biometricKey)
                val then = fi.iki.ede.dateutils.DateUtils.newParse(String(decrypted))
                val age = fi.iki.ede.dateutils.DateUtils.getPeriodBetweenDates(then)
                if (age.days < 128) {
                    LoginHandler.biometricLogin()
                    return true
                }
                // TODO: Biometrics is too old..force re-registration, be nice, tell user too!
                Log.e(TAG, "Biometrics too old, re-registering")
            } catch (ex: Exception) {
                Log.i("Biometrics", "Error $ex")
            }
            clearBiometricKeys()
            return false
        }

        fun getVerificationIntent(context: Context) =
            Intent(context, BiometricsActivity::class.java).setAction(BIO_VERIFY)

        fun getRegistrationIntent(context: Context) =
            Intent(context, BiometricsActivity::class.java).setAction(BIO_INITIALIZE)

        fun clearBiometricKeys() = sharedPreferences.edit()
            .remove(PREFERENCE_BIO_CIPHER)
            .apply()

        private fun getBioCipher(): IVCipherText {
            val pm = sharedPreferences
                .getString(PREFERENCE_BIO_CIPHER, null) ?: return IVCipherText.getEmpty()
            return IVCipherText(CipherUtilities.IV_LENGTH, pm.hexToByteArray())
        }

        private fun storeBioCipher(cipher: IVCipherText) = sharedPreferences.edit()
            .putString(PREFERENCE_BIO_CIPHER, cipher.combineIVAndCipherText().toHexString())
            .apply()

        const val RESULT_FAILED = 1
        private const val BIO_INITIALIZE = "bioinitialize"
        private const val BIO_VERIFY = "bioverify"
    }
}
