package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.composable.AskToRestoreDatabase
import fi.iki.ede.safe.ui.composable.passwordTextField
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.runBlocking


class RestoreDatabaseScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        Preferences.setBackupDocument(this, intent.dataString)
        val compatibility = intent.getBooleanExtra(OISAFE_COMPATIBILITY, false)

        setContent {
            SafeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var doRestore by remember { mutableStateOf(false) }
                    val selectedDoc = intent.data!!
                    Column {
                        Text(
                            text = stringResource(
                                id = R.string.restore_screen_backup_help,
                                selectedDoc.toString()
                            )
                        )
                        val backupPassword =
                            passwordTextField(textTip = R.string.restore_screen_backups_password)
                        Button(onClick = {
                            doRestore = true
                            // Disable the button? Progress?
                            // Toasts are bit bad..
                        }) {
                            Text(text = "Restore")
                        }
                        if (doRestore) {
                            doRestore = false
                            Toast.makeText(
                                context,
                                stringResource(id = R.string.restore_screen_begin_restore),
                                Toast.LENGTH_LONG
                            )
                                .show()
                            val passwords = AskToRestoreDatabase(
                                context as AvertInactivityDuringLongTask,
                                context,
                                compatibility,
                                backupPassword,
                                selectedDoc
                            )
                            val result =
                                if (passwords > 0) {
                                    // No point clearing biometrics(passkey not tied to masterkey)
                                    //Biometrics.clearBiometricKeys(context)
                                    // TODO: MAKE ASYNC
                                    runBlocking {
                                        DataModel.loadFromDatabase()
                                    }
                                    Toast.makeText(
                                        context,
                                        getString(R.string.restore_screen_restored, passwords),
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                    RESULT_OK
                                } else {
                                    Toast.makeText(
                                        context,
                                        stringResource(id = R.string.restore_screen_restore_failed),
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                    RESULT_CANCELED
                                }
                            CategoryListScreen.startMe(context)
                            context.setResult(result)
                            context.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val OISAFE_COMPATIBILITY = "oisafe_compatibility"
        fun startMe(context: Context, uri: Uri, oisafeCompatibility: Boolean) {
            context.startActivity(
                getIntent(context, oisafeCompatibility, uri)
            )
        }

        fun getIntent(
            context: Context,
            oisafeCompatibility: Boolean,
            uri: Uri
        ) = Intent(
            context,
            RestoreDatabaseScreen::class.java
        ).putExtra(OISAFE_COMPATIBILITY, oisafeCompatibility)
            .let {
                it.data = uri
                it
            }
    }
}
