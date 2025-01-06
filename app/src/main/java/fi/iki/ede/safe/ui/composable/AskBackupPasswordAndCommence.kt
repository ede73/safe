package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R


@Composable
fun AskBackupPasswordAndCommence(
    processedPasswords: MutableIntState,
    processedCategories: MutableIntState,
    processedMessage: MutableState<String>,
    selectedDoc: Uri,
    context: Context,
    doBackup: @Composable (backupPassword: Password) -> Unit
) {
    fi.iki.ede.theme.SafeTheme {
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
                fi.iki.ede.theme.SafeButton(
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
                    doBackup(backupPassword)
                }
            }
        }
    }
}


//@Preview(showBackground = true)
//@Composable
//fun PrepareDBRestorePreview() {
//    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
//    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
//    SafeTheme {
//        AskBackupPasswordAndCommence(
//            processedPasswords = remember { mutableIntStateOf(0) },
//            processedCategories = remember { mutableIntStateOf(0) },
//            processedMessage = remember { mutableStateOf("") },
//            selectedDoc = Uri.EMPTY,
//            context = PrepareDataBaseRestorationScreen(),
//            { _ -> }
//        )
//    }
//}