package fi.iki.ede.safe.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.service.AutolockingService
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.composable.BiometricsComponent
import fi.iki.ede.safe.ui.composable.PasswordPrompt
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.startActivityForResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class LoginScreen : ComponentActivity() {
    private var oenCategoryListScreen: Boolean = true
    private val biometricsFirstTimeRegister = biometricsFirstTimeActivity()
    private val biometricsVerify = biometricsVerifyActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oenCategoryListScreen = intent.getBooleanExtra(
            OPEN_CATEGORY_SCREEN_AFTER_LOGIN, true
        )
        if (BuildConfig.DEBUG) {
            val isBundleTest = intent.getStringExtra("isBundleTest") == "true"
            PluginManager.setBundleTestMode(isBundleTest)
        }
        // "upgrade" from old versions where first time login was solely
        // determined by existence of passwords in the database
        // we don't really know if this is first time login or not
        // (unless we literally DO go snooping in the database)

        val firstTimeUse = Preferences.isFirstTimeLogin()
        setContent {
            LoginScreenCompose(
                firstTimeUse,
                ::goodPasswordEntered,
                biometricsVerify
            )
        }
    }

    private fun goodPasswordEntered(
        firstTimeUse: Boolean,
        it: Password,
    ) {
        val context = this
        val passwordIsAccepted = if (firstTimeUse) {
            LoginHandler.firstTimeLogin(context, it)
            true
        } else {
            LoginHandler.passwordLogin(context, it)
        }

        if (passwordIsAccepted) {
            val registerBiometricsActivity =
                (firstTimeUse && BiometricsActivity.isBiometricEnabled())
                        || (BiometricsActivity.isBiometricEnabled() &&
                        !BiometricsActivity.haveRecordedBiometric())
            if (registerBiometricsActivity) {
                biometricsFirstTimeRegister.launch(BiometricsActivity.getRegistrationIntent(context))
            } else {
                finishLoginProcess(firstTimeUse)
            }
        }
    }

    private fun finishLoginProcess(firstTimeUse: Boolean) {
        beginToLoadDB(firstTimeUse)

        // We've LOGGED IN, so we must have master key ready and done
        // Mitigate coming from old client where first time login preference
        // wasn't used
        Preferences.setMasterkeyInitialized()
        setResult(RESULT_OK, Intent())
        startService(Intent(applicationContext, AutolockingService::class.java))
        if (oenCategoryListScreen) {
            IntentManager.startCategoryScreen(this)
        }
        finish()
    }

    private fun beginToLoadDB(firstTimeUse: Boolean) {
        val myScope = CoroutineScope(Dispatchers.Main)
        myScope.launch {
            withContext(Dispatchers.IO) {
                DataModel.loadFromDatabase()

                if (firstTimeUse) {
                    val entry = DecryptableCategoryEntry().apply {
                        encryptedName = KeyStoreHelperFactory.getEncrypter().invoke(
                            ("Category - Long press to edit".toByteArray())
                        )
                    }
                    DataModel.addOrEditCategory(entry)

                    // TODO: well, do add some passwords as well
                }
            }
        }
    }

    private fun biometricsFirstTimeActivity() =
        startActivityForResults(TestTag.TEST_TAG_LOGIN_BIOMETRICS_REGISTER) { result ->
            // This is FIRST TIME call..we're just about to be set up...
            when (result.resultCode) {
                RESULT_OK -> {
                    BiometricsActivity.registerBiometric(this)
                    finishLoginProcess(true)
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                }
                // may be called many times..eventually gets cancelled
                BiometricsActivity.RESULT_FAILED -> {}
            }
        }

    private fun biometricsVerifyActivity() =
        startActivityForResults(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    if (!BiometricsActivity.verificationAccepted()) {
                        // should never happen
                        Log.e("---", "Biometric verification NOT accepted - perhaps a new backup?")
                    } else {
                        finishLoginProcess(false)
                    }
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                }

                BiometricsActivity.RESULT_FAILED -> {
                    // Sometimes fingerprint reading just doesn't work
                    // This will be called for N subsequent misreads
                    // Then biometrics will be cancelled
                }
            }
        }

    companion object {
        const val OPEN_CATEGORY_SCREEN_AFTER_LOGIN = "open_category_screen"
    }
}

@Composable
private fun LoginScreenCompose(
    firstTimeUse: Boolean,
    goodPasswordEntered: (Boolean, Password) -> Unit,
    biometricsVerify: ActivityResultLauncher<Intent>? = null,
) {
    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopActionBar(loginScreen = true)
                PasswordPrompt(firstTimeUse) { it: Password ->
                    goodPasswordEntered(firstTimeUse, it)
                }
                // TODO: if we're fresh from backup - biometrics don't work
                biometricsVerify?.let { BiometricsComponent(it) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeTheme {
        LoginScreenCompose(
            firstTimeUse = true,
            goodPasswordEntered = { _, _ -> /* No-op for preview */ },
        )
    }
}