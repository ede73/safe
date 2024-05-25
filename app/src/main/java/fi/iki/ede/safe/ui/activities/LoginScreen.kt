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
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.service.AutoLockService
import fi.iki.ede.safe.ui.composable.BiometricsComponent
import fi.iki.ede.safe.ui.composable.PasswordPrompt
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class LoginScreen : ComponentActivity() {
    private var dontOpenCategoryListScreen: Boolean = false
    private val biometricsInitialize =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            // This is FIRST TIME call..we're just about to be set up...
            when (result.resultCode) {
                RESULT_OK -> {
                    Biometrics.registerBiometric(this)
                    passwordValidatedStartAutolockServiceAndFinish()
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                    //mSkipBiometrics = true
                }
                // may be called many times..eventually gets cancelled
                Biometrics.RESULT_FAILED -> {}
            }
        }

    private val biometricsVerify =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            when (result.resultCode) {
                RESULT_OK -> {
                    if (!Biometrics.verificationAccepted(this)) {
                        // should never happen
                        Log.e("---", "Biometric verification NOT accepted - perhaps a new backup?")
                    } else {
                        passwordValidatedStartAutolockServiceAndFinish()
                    }
                }

                RESULT_CANCELED -> {
                    // We should fall back asking password but NOT disable biometrics
                    //mSkipBiometrics = true
                }

                Biometrics.RESULT_FAILED -> {
                    // Sometimes fingerprint reading just doesn't work
                    // This will be called for N subsequent misreads
                    // Then biometrics will be cancelled
                }
            }
        }

    private fun passwordValidatedStartAutolockServiceAndFinish() {
        setResult(RESULT_OK, Intent())
        startService(Intent(applicationContext, AutoLockService::class.java))
        val myScope = CoroutineScope(Dispatchers.Main)
        myScope.launch {
            withContext(Dispatchers.IO) {
                DataModel.loadFromDatabase()
            }
        }
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
        val firstTimeUse = DBHelperFactory.getDBHelper(context).isUninitializedDatabase()

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
        val passwordValidated =
            if (firstTimeUse) {
                LoginHandler.firstTimeLogin(context, it)
                true
            } else {
                LoginHandler.passwordLogin(context, it)
            }

        if (passwordValidated) {
            passwordValidated(firstTimeUse, context)
        }
    }

    private fun passwordValidated(
        firstTimeUse: Boolean,
        context: LoginScreen,
    ) {
        val registerBiometrics = (firstTimeUse && Biometrics.isBiometricEnabled(context))
                || (Biometrics.isBiometricEnabled(context) &&
                !Biometrics.haveRecordedBiometric(context))
        if (registerBiometrics) {
            biometricsInitialize.launch(Biometrics.getRegistrationIntent(context))
        } else {
            passwordValidatedStartAutolockServiceAndFinish()
        }
    }

    companion object {
        const val TESTTAG_PASSWORD_PROMPT = "password"
        const val TESTTAG_LOGIN_BUTTON = "login"
        const val TESTTAG_BIOMETRICS_BUTTON = "login_with_biometrics"
        const val TESTTAG_BIOMETRICS_CHECKBOX = "biometrics_enable"
        const val DONT_OPEN_CATEGORY_SCREEN_AFTER_LOGIN = "dont_open_category_screen"
        fun startMe(context: Context, dontOpenCategoryScreenAfterLogin: Boolean = false) {
            if (DBHelperFactory.getDBHelper(context).isUninitializedDatabase()) {
                return
            }

            context.startActivity(
                Intent(
                    context,
                    LoginScreen::class.java
                ).putExtra(DONT_OPEN_CATEGORY_SCREEN_AFTER_LOGIN, dontOpenCategoryScreenAfterLogin)
            )
//            context.startActivity(Intent(context, LoginScreen::class.java)
//                .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).let { it ->
//                    if (action != null) it.action = action
//                    it
//                })
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