package fi.iki.ede.safe.ui.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.composable.PasswordTextField
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.runBlocking


class PrepareDataBaseRestorationScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val compatibility = intent.getBooleanExtra(OISAFE_COMPATIBILITY, false)
        val selectedDoc = intent.data!!
        Preferences.setBackupDocument(intent.dataString)

        setContent {
            PrepareDBRestoreCompose(selectedDoc, this, compatibility, ::avertInactivity)
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
            context, PrepareDataBaseRestorationScreen::class.java
        ).putExtra(OISAFE_COMPATIBILITY, oisafeCompatibility)
            .apply { data = uri }
    }
}


@Composable
private fun PrepareDBRestoreCompose(
    selectedDoc: Uri,
    context: PrepareDataBaseRestorationScreen,
    compatibility: Boolean,
    avertInactivity: (Context, String) -> Unit
) {
    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            var doRestore by remember { mutableStateOf(false) }
            var backupPassword by remember { mutableStateOf(Password.getEmpty()) }
            val toast = remember { mutableStateOf("") }
            Column {
                if (toast.value.isNotEmpty()) {
                    Toast.makeText(context, toast.value, Toast.LENGTH_LONG).show()
                    toast.value = ""
                }
                Text(
                    text = stringResource(
                        id = R.string.restore_screen_backup_help,
                        selectedDoc.toString()
                    )
                )
                PasswordTextField(textTip = R.string.restore_screen_backups_password,
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
                    val toastContext = context
                    RestoreDatabaseComponent(
                        context,
                        compatibility,
                        backupPassword,
                        selectedDoc,
                        onFinished = { restoredPasswords, ex ->
                            // YES, we could have 0 passwords, but 1 category
                            if (ex == null) {
                                // TODO: MAKE ASYNC
                                runBlocking {
                                    DataModel.loadFromDatabase()
                                }
                                toast.value = context.getString(
                                    R.string.restore_screen_restored,
                                    restoredPasswords
                                )
                                CategoryListScreen.startMe(context)
                                context.setResult(RESULT_OK)
                                context.finish()
                            } else {
                                toast.value =
                                    context.getString(R.string.restore_screen_restore_failed)
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


@Preview(showBackground = true)
@Composable
fun PrepareDBRestorePreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeTheme {
        PrepareDBRestoreCompose(
            selectedDoc = Uri.EMPTY,
            context = PrepareDataBaseRestorationScreen(),
            compatibility = false,
            avertInactivity = { _, _ -> }
        )
    }
}