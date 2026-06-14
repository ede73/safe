package fi.iki.ede.safe.ui.composable

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.backup.MyBackupAgent
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelper.Companion.ANDROID_KEYSTORE
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
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
                if (biometricsActivityEnabled && biometricsRecorded && !ks.containsAlias("biokey")) {
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomActionBar(loginScreen = true) }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                LoginPasswordPrompts(loginPrecondition) { loginStyle, pwd ->
                    goodPasswordEntered(loginStyle, pwd)
                }
                if (loginPrecondition != LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE) {
                    // TODO: if we're fresh from backup - biometrics don't work
                    biometricsVerify?.let {
                        KeyStoreHelperFactory.provideKeyStoreHelper =
                            KeyStoreHelper(KeyStore.getInstance(ANDROID_KEYSTORE))
                        BiometricsComponent(it)
                    }
                }

                // just FYI
                if (!LocalInspectionMode.current && MyBackupAgent.haveRestoreMark(context)) {
                    val time =
                        Preferences.getAutoBackupRestoreFinished()
                            ?.toLocalDateTime()?.toString()
                            ?: ""
                    Text(
                        stringResource(R.string.login_screen_restore_mark_message, time),
                        modifier = Modifier.border(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
                }
            }
        }
    }
}
