package fi.iki.ede.safe.ui.composable

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.backup.MyBackupAgent
import fi.iki.ede.crypto.Password
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.activities.LoginPrecondition
import fi.iki.ede.safe.ui.activities.LoginStyle
import fi.iki.ede.safe.ui.activities.isGoodRestoredContent
import fi.iki.ede.theme.SafeTheme

@Composable
internal fun LoginScreenCompose(
    loginPrecondition: LoginPrecondition,
    goodPasswordEntered: (LoginStyle, Password) -> Boolean,
    biometricsVerify: ActivityResultLauncher<Intent>? = null,
) {
    SafeTheme {
        val context = LocalContext.current

        val weHaveRestoredDatabase = isGoodRestoredContent(context)
        // there is one big caveat now
        // IF our data was indeed restored from backup
        // making NEW login will basically render our database un-readable(???)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopActionBar(loginScreen = true)
                LoginPasswordPrompts(loginPrecondition) { loginStyle, pwd ->
                    goodPasswordEntered(loginStyle, pwd)
                }
                // TODO: if we're fresh from backup - biometrics don't work
                biometricsVerify?.let { BiometricsComponent(it) }

                // just FYI
                if (MyBackupAgent.haveRestoreMark(context)) {
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