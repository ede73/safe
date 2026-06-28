package fi.iki.ede.safe.ui.composable

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
import fi.iki.ede.crypto.Password
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeTheme

@Composable
fun AskBackupPasswordAndCommence(
    processedPasswords: MutableIntState,
    processedCategories: MutableIntState,
    processedMessage: MutableState<String>,
    selectedDocName: String,
    doBackup: @Composable (backupPassword: Password) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        var doRestore by remember { mutableStateOf(false) }
        var backupPassword by remember { mutableStateOf(Password.getEmpty()) }
        Column {
            Text(
                text = getString("restore_screen_backup_help").replace("%s", selectedDocName).replace("%1\$s", selectedDocName)
            )
            PasswordTextField(
                label = getString("restore_screen_backups_password"),
                onValueChange = {
                    backupPassword = it
                })
            SafeButton(
                onClick = {
                    doRestore = true
                },
                enabled = !doRestore
            ) {
                Text(text = getString("restore_screen_restore_button"))
            }
            Column {
                Text(getString("restore_screen_passwords_count").replace("%d", processedPasswords.intValue.toString()))
                Text(getString("restore_screen_categories_count").replace("%d", processedCategories.intValue.toString()))
                val msg = processedMessage.value
                val localizedMessage = when (msg) {
                    "Begin restoration" -> getString("restore_screen_begin_restore")
                    "Process backup" -> getString("restore_screen_process_backup")
                    "Finished with backup" -> getString("restore_screen_finished_backup")
                    "Something failed, rollback" -> getString("restore_screen_restore_failed")
                    "Restoring old backup" -> getString("restore_screen_restoring_old_backup")
                    else -> msg
                }
                Text(localizedMessage)
            }
            if (doRestore) {
                doBackup(backupPassword)
            }
        }
    }
}
