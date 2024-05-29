package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.service.AutolockingService
import fi.iki.ede.safe.ui.composable.BiometricsComponent
import fi.iki.ede.safe.ui.composable.PasswordPrompt
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class LoginScreen : ComponentActivity() {
    private var dontOpenCategoryListScreen: Boolean = false
    private val biometricsFirstTimeRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
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

    private val biometricsVerify =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
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

    private fun finishLoginProcess(firstTimeUse: Boolean) {
        beginToLoadDB(firstTimeUse)

        setResult(RESULT_OK, Intent())
        startService(Intent(applicationContext, AutolockingService::class.java))
        if (!dontOpenCategoryListScreen) {
            CategoryListScreen.startMe(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        dontOpenCategoryListScreen = intent.getBooleanExtra(
            DONT_OPEN_CATEGORY_SCREEN_AFTER_LOGIN, false
        )
        val firstTimeUse = Preferences.isFirstTimeLogin()

        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        PasswordPrompt(firstTimeUse) { it: Password ->
                            goodPasswordEntered(
                                firstTimeUse,
                                context,
                                it,
                            )
                        }
                        // TODO: if we're fresh from backup - biometrics don't work
                        BiometricsComponent(
                            biometricsVerify,
                        )
                    }
                }
            }
        }
    }

    private fun goodPasswordEntered(
        firstTimeUse: Boolean,
        context: LoginScreen,
        it: Password,
    ) {
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
                biometricsFirstTimeRegister.launch(
                    BiometricsActivity.getRegistrationIntent(context)
                )
            } else {
                finishLoginProcess(firstTimeUse)
            }
        }
    }

    private fun beginToLoadDB(firstTimeUse: Boolean) {
        val myScope = CoroutineScope(Dispatchers.Main)
        myScope.launch {
            withContext(Dispatchers.IO) {
                DataModel.loadFromDatabase()

                if (firstTimeUse) {
                    val entry = DecryptableCategoryEntry().apply {
                        encryptedName = KeyStoreHelperFactory.getKeyStoreHelper()
                            .encryptByteArray("Category - Long press to edit".toByteArray())
                    }
                    DataModel.addOrEditCategory(entry)

                    // TODO: well, do add some passwords as well
                }
            }
        }
    }

    companion object {
        const val TESTTAG_PASSWORD_PROMPT = "password"
        const val TESTTAG_LOGIN_BUTTON = "login"
        const val TESTTAG_BIOMETRICS_BUTTON = "login_with_biometrics"
        const val TESTTAG_BIOMETRICS_CHECKBOX = "biometrics_enable"
        const val DONT_OPEN_CATEGORY_SCREEN_AFTER_LOGIN = "dont_open_category_screen"
        fun startMe(context: Context, dontOpenCategoryScreenAfterLogin: Boolean = false) {
            context.startActivity(
                Intent(
                    context,
                    LoginScreen::class.java
                ).putExtra(DONT_OPEN_CATEGORY_SCREEN_AFTER_LOGIN, dontOpenCategoryScreenAfterLogin)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    SafeTheme {
        Column {
            PasswordPrompt(true, goodPasswordEntered = {})
            // Accessing biometrics makes this fail...
            //BiometricsComponent()
        }
    }
}