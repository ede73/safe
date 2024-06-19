package fi.iki.ede.safe.ui.activities

import android.content.Context
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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.composable.PasswordTextField
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PrepareDataBaseRestorationScreen : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedDoc = intent.data!!
        Preferences.setBackupDocument(intent.dataString)

        val context = this
        setContent {
            val coroutineScope = rememberCoroutineScope()
            val processedPasswords = remember { mutableIntStateOf(0) }
            val processedCategories = remember { mutableIntStateOf(0) }
            val processedMessage = remember { mutableStateOf("") }

            AskBackupPasswordAndCommence(
                processedPasswords,
                processedCategories,
                processedMessage,
                selectedDoc,
                this,
                ::avertInactivity
            ) { backupPassword ->
                RestoreDatabaseComponent(
                    processedPasswords,
                    processedCategories,
                    processedMessage,
                    context,
                    backupPassword,
                    selectedDoc,
                    onFinished = { restoredPasswords, ex ->
                        // YES, we could have 0 passwords, but 1 category
                        if (ex == null) {
                            processedMessage.value = "Re-read database"
                            coroutineScope.launch(Dispatchers.IO) {
                                DataModel.loadFromDatabase()
                            }
                            processedMessage.value = "Done!"
                            IntentManager.startCategoryScreen(context)
                            context.setResult(RESULT_OK)
                            context.finish()
                        } else {
                            processedMessage.value =
                                context.getString(R.string.restore_screen_restore_failed)
                            IntentManager.startCategoryScreen(context)
                            context.setResult(RESULT_CANCELED)
                            context.finish()
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun AskBackupPasswordAndCommence(
    processedPasswords: MutableIntState,
    processedCategories: MutableIntState,
    processedMessage: MutableState<String>,
    selectedDoc: Uri,
    context: Context,
    avertInactivity: (Context, String) -> Unit,
    doBackup: @Composable (backupPassword: Password) -> Unit
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
                SafeButton(
                    onClick = {
                        doRestore = true
                        // Disable the button? Progress?
                        // Toasts are bit bad..
                    },
                    enabled = !doRestore
                ) {
                    Text(text = "Restore")
                }
                Column {
                    Text("Passwords ${processedPasswords.intValue}")
                    Text("Categories ${processedCategories.intValue}")
                    Text(processedMessage.value)
                }
                if (doRestore) {
                    avertInactivity(context, "Begin database restoration")
                    doBackup(backupPassword)
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
        AskBackupPasswordAndCommence(
            processedPasswords = remember { mutableIntStateOf(0) },
            processedCategories = remember { mutableIntStateOf(0) },
            processedMessage = remember { mutableStateOf("") },
            selectedDoc = Uri.EMPTY,
            context = PrepareDataBaseRestorationScreen(),
            avertInactivity = { _, _ -> },
            { _ -> }
        )
    }
}