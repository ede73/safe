package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import java.time.ZonedDateTime

// TODO: With latest jetpack biometric lib, authentication failed flow seems to have changed
// Previously there were couple of tries, not it flat out fails
// End result is login screen activates, but biometric screen still stays on, so user had to
// navigate back, then enter password or launch biometric again
// Not a big deal, but feels buggy
class BiometricsActivity : AppCompatActivity() {

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
        finishActivity(RESULT_CANCELED)
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
        fun isBiometricEnabled(context: Context) = Preferences.getBiometricsEnabled(context, false)
        fun setBiometricEnabled(context: Context, value: Boolean) =
            Preferences.setBiometricsEnabled(context, value)

        fun haveRecordedBiometric(context: Context) = Preferences.getBioCipher(context).isNotEmpty()

        /**
         * Call after biometrics initialize if RESULT_OK (ie. user's biometrics was recognized)
         * We shall store the masterkey to KeyStore (and use it for subsequent biometric
         * verifications)
         */
        fun registerBiometric(context: Context) {
            // encrypt something funny with biokey
            val ks = KeyStoreHelperFactory.getKeyStoreHelper()
            val biokey = ks.getOrCreateBiokey()
            val now = ZonedDateTime.now().toEpochSecond().toString()
            val stamp = ks.encryptByteArray(now.toByteArray(), biokey)
            Preferences.storeBioCipher(context, stamp)
            LoginHandler.biometricLogin()
        }

        /**
         * Call after successful biometric verification
         *
         * TODO: Implement some check - keystore has separate biokey in android keystore
         */
        fun verificationAccepted(context: Context): Boolean {
            val stampCipher = Preferences.getBioCipher(context)
            val ks = KeyStoreHelperFactory.getKeyStoreHelper()
            val biokey = ks.getOrCreateBiokey()
            try {
                val decrypted = ks.decryptByteArray(stampCipher, biokey)
                val then = DateUtils.newParse(String(decrypted))
                val age = DateUtils.getPeriodBetweenDates(then)
                if (age.days < 128) {
                    LoginHandler.biometricLogin()
                    return true
                }
                // Biometrics is too old..force re-registration
            } catch (ex: Exception) {
                Log.i("Biometrics", "Error $ex")
            }
            Preferences.clearBioCipher(context)
            return false
        }

        fun getVerificationIntent(context: Context) =
            Intent(context, BiometricsActivity::class.java).setAction(BIO_VERIFY)

        fun getRegistrationIntent(context: Context) =
            Intent(context, BiometricsActivity::class.java).setAction(BIO_INITIALIZE)

        fun clearBiometricKeys(context: Context) = Preferences.clearBioCipher(context)


        const val RESULT_FAILED = 1
        private const val BIO_INITIALIZE = "bioinitialize"
        private const val BIO_VERIFY = "bioverify"
    }
}