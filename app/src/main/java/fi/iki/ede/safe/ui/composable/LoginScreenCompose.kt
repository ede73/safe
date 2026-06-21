package fi.iki.ede.safe.ui.composable

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.integerResource
import fi.iki.ede.backup.MyBackupAgent
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelper.Companion.ANDROID_KEYSTORE
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.SharedLoginScreen
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.LoginPrecondition
import fi.iki.ede.safe.ui.activities.LoginStyle
import fi.iki.ede.safe.ui.activities.isGoodRestoredContent
import fi.iki.ede.theme.SafeTheme
import java.security.KeyStore
import kotlin.time.ExperimentalTime

@ExperimentalFoundationApi
@ExperimentalTime
@Composable
internal fun LoginScreenCompose(
    loginPrecondition: LoginPrecondition,
    goodPasswordEntered: (LoginStyle, Password) -> Boolean,
    biometricsVerify: ActivityResultLauncher<Intent>? = null,
) {
    SafeTheme {
        val context = LocalContext.current
        isGoodRestoredContent(context)
        // there is one big caveat now
        // IF our data was indeed restored from backup
        // making NEW login will basically render our database un-readable(???)

        // Check if biometrics should be auto-launched
        val biometricsActivityEnabled = BiometricsActivity.isBiometricEnabled()
        val biometricsRecorded = BiometricsActivity.haveRecordedBiometric()
        val keystoreIsInitialized = remember {
            try {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
                ks.load(null)
                if (biometricsActivityEnabled && biometricsRecorded && !ks.containsAlias("biometrics")) {
                    BiometricsActivity.clearBiometricKeys()
                }
                true
            } catch (ex: Exception) {
                BiometricsActivity.clearBiometricKeys()
                false
            }
        }

        // Auto-launch biometrics if enabled
        var hasLaunchedBiometrics by remember { mutableStateOf(false) }
        if (loginPrecondition != LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE &&
            biometricsActivityEnabled && biometricsRecorded && keystoreIsInitialized &&
            biometricsVerify != null && !hasLaunchedBiometrics
        ) {
            LaunchedEffect(Unit) {
                hasLaunchedBiometrics = true
                KeyStoreHelperFactory.provideKeyStoreHelper =
                    KeyStoreHelper(KeyStore.getInstance(ANDROID_KEYSTORE))
                biometricsVerify.launch(BiometricsActivity.getVerificationIntent(context))
            }
        }

        val isInspectionMode = LocalInspectionMode.current
        val initialStatusMessage = remember {
            if (!isInspectionMode && MyBackupAgent.haveRestoreMark(context)) {
                val time = Preferences.getAutoBackupRestoreFinished()?.toLocalDateTime()?.toString() ?: ""
                context.getString(R.string.login_screen_restore_mark_message, time)
            } else ""
        }

        var statusMessage by remember { mutableStateOf(initialStatusMessage) }

        val passwordMinimumLength = integerResource(id = R.integer.password_minimum_length)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomActionBar(loginScreen = true) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                SharedLoginScreen(
                    isFirstTimeLogin = loginPrecondition == LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE,
                    isBiometricsEnabled = biometricsActivityEnabled && biometricsRecorded && keystoreIsInitialized,
                    passwordMinimumLength = passwordMinimumLength,
                    statusMessage = statusMessage,
                    onCreateVault = { pwd, registerBio ->
                        BiometricsActivity.setBiometricEnabled(registerBio)
                        val accepted = goodPasswordEntered(LoginStyle.FIRST_TIME_LOGIN_CLEAR_DATABASE, Password(pwd.toByteArray()))
                        if (!accepted) {
                            statusMessage = context.getString(R.string.failed_to_create_vault)
                        }
                    },
                    onUnlock = { pwd, registerBio ->
                        BiometricsActivity.setBiometricEnabled(registerBio)
                        val accepted = goodPasswordEntered(LoginStyle.EXISTING_LOGIN, Password(pwd.toByteArray()))
                        if (!accepted) {
                            statusMessage = context.getString(R.string.login_invalid_password)
                        }
                    },
                    onBiometricLogin = if (biometricsVerify != null && biometricsActivityEnabled && biometricsRecorded && keystoreIsInitialized) {
                        {
                            KeyStoreHelperFactory.provideKeyStoreHelper =
                                KeyStoreHelper(KeyStore.getInstance(ANDROID_KEYSTORE))
                            biometricsVerify.launch(BiometricsActivity.getVerificationIntent(context))
                        }
                    } else null
                )
            }
        }
    }
}
