package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.composable.passwordTextField
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.runBlocking


class PrepareDataBaseRestorationScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this
        val compatibility = intent.getBooleanExtra(OISAFE_COMPATIBILITY, false)

        Preferences.setBackupDocument(intent.dataString)
        val selectedDoc = intent.data!!

        setContent {
            SafeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var doRestore by remember { mutableStateOf(false) }
                    var backupPassword by remember { mutableStateOf(Password.getEmpty()) }
                    Column {
                        Text(
                            text = stringResource(
                                id = R.string.restore_screen_backup_help,
                                selectedDoc.toString()
                            )
                        )
                        passwordTextField(textTip = R.string.restore_screen_backups_password,
                            onValueChange = {
                                backupPassword = it
                            })
                        SafeButton(enabled = !doRestore,
                            onClick = {
                                doRestore = true
                                // Disable the button? Progress?
                                // Toasts are bit bad..
                            }) {
                            Text(text = "Restore")
                        }
                        if (doRestore) {
                            // TODO: proper progress bar would be nice
                            Toast.makeText(
                                context,
                                stringResource(id = R.string.restore_screen_begin_restore),
                                Toast.LENGTH_LONG
                            ).show()

                            avertInactivity(context, "Begin database restoration")
                            RestoreDatabaseComponent(
                                context,
                                compatibility,
                                backupPassword,
                                selectedDoc,
                                onFinished = { restoredPasswords, ex ->
                                    // YES, we could have 0 passwords, but 1 category
                                    if (ex == null) {
                                        // No point clearing biometrics(passkey not tied to masterkey)
                                        //Biometrics.clearBiometricKeys(context)
                                        // TODO: MAKE ASYNC
                                        runBlocking {
                                            DataModel.loadFromDatabase()
                                        }
//                                        Toast.makeText(
//                                            context,
//                                            getString(
//                                                R.string.restore_screen_restored,
//                                                restoredPasswords
//                                            ),
//                                            Toast.LENGTH_LONG
//                                        ).show()
                                        CategoryListScreen.startMe(context)
                                        context.setResult(RESULT_OK)
                                        context.finish()
                                    } else {
                                        // java.lang.NullPointerException: Can't toast on a thread that has not called Looper.prepare()
//                                        Toast.makeText(
//                                            context,
//                                            getString(R.string.restore_screen_restore_failed),
//                                            Toast.LENGTH_LONG
//                                        ).show()
                                        CategoryListScreen.startMe(context)
                                        context.setResult(RESULT_CANCELED)
                                        context.finish()
                                    }
                                }
                            )
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
            PrepareDataBaseRestorationScreen::class.java
        ).putExtra(OISAFE_COMPATIBILITY, oisafeCompatibility)
            .let {
                it.data = uri
                it
            }
    }
}