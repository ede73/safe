package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.BackupDatabase
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.gpm.ui.activities.ImportGooglePasswords
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.splits.DropDownMenu
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TopActionBar"

@Composable
fun setupActivityResultLauncher(resultOk: (ActivityResult) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        when (it.resultCode) {
            Activity.RESULT_OK -> {
                resultOk(it)
            }
        }
    }
}

/**
 * Used in category and password list views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopActionBar(
    onAddRequested: (addCompleted: ManagedActivityResultLauncher<Intent, ActivityResult>) -> Unit = {},
    loginScreen: Boolean = false,
) {
    val displayMenu = remember { mutableStateOf(false) }
    val exportImport = remember { mutableStateOf(false) }
    val showChangePasswordDialog = remember { mutableStateOf(false) }
    val toast = remember { mutableStateOf("") }

    val context = LocalContext.current

    if (toast.value != "") {
        Toast.makeText(context, toast.value, Toast.LENGTH_LONG).show()
        toast.value = ""
    }

    val addCompleted = setupActivityResultLauncher {/*  nothing to do anymore (thanks flow!)*/ }

    SafeTheme {
        TopAppBar(title = {
            Text(
                stringResource(id = R.string.application_name),
                color = SafeTheme.colorScheme.onSurface
            )
        }, actions = {
            if (!loginScreen) {
                IconButton(onClick = {
                    onAddRequested(addCompleted)
                }, modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_ADD)) {
                    Icon(Icons.Default.Add, stringResource(id = R.string.generic_add))
                }

                IconButton(onClick = {
                    AutolockingBaseComponentActivity.lockTheApplication(context)
                    IntentManager.startLoginScreen(
                        context, openCategoryScreenAfterLogin = false
                    )
                }) {
                    Icon(Icons.Default.Lock, stringResource(id = R.string.action_bar_lock))
                }

                IconButton(
                    onClick = { IntentManager.startSiteEntrySearchScreen(context) },
                    modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_SEARCH)
                ) {
                    Icon(Icons.Default.Search, stringResource(id = R.string.action_bar_search))
                }
            }

            IconButton(onClick = { displayMenu.value = !displayMenu.value }) {
                Icon(Icons.Default.MoreVert, "")
            }

            MakeDropdownMenu(
                loginScreen, displayMenu, exportImport, showChangePasswordDialog, toast
            )

            if (showChangePasswordDialog.value) {
                ShowChangeMasterPasswordDialog(showChangePasswordDialog)
            }
        })
    }
}

@Composable
private fun ShowChangeMasterPasswordDialog(
    showChangePasswordDialog: MutableState<Boolean>
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val masterPasswordChanged = stringResource(id = R.string.action_bar_password_changed)
    val masterPasswordChangeFailed = stringResource(id = R.string.action_bar_password_change_failed)

    EnterNewMasterPassword {
        val (oldMasterPassword, newMasterPassword) = it
        if (!oldMasterPassword.isEmpty() && !newMasterPassword.isEmpty()) {
            ChangeMasterKeyAndPassword.changeMasterPassword(
                oldMasterPassword, newMasterPassword
            ) { success ->
                // NOTICE! This isn't a UI thread!
                coroutineScope.launch(Dispatchers.Main) {
                    if (success) {
                        // master password successfully changed
                        Toast.makeText(
                            context, masterPasswordChanged, Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context, masterPasswordChangeFailed, Toast.LENGTH_LONG
                        ).show()
                    }
                }
                showChangePasswordDialog.value = false
            }
        } else {
            showChangePasswordDialog.value = false
        }
    }
}

@Composable
private fun MakeDropdownMenu(
    loginScreen: Boolean,
    displayMenu: MutableState<Boolean>,
    exportImport: MutableState<Boolean>,
    showChangePasswordDialog: MutableState<Boolean>,
    toast: MutableState<String>,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val restoreCompleted =
        setupActivityResultLauncher {/* don't case, success->get new ones, fail->got old ones */ }

    val selectRestoreDocumentLauncher = setupActivityResultLauncher {
        restoreCompleted.launch(
            IntentManager.getDatabaseRestorationScreenIntent(context, it.data!!.data!!)
        )
    }

    val backupCompleted = stringResource(id = R.string.action_bar_backup_completed)

    val backupDocumentSelectedResult = setupActivityResultLauncher {
        if (it.data?.data != null) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    initiateBackup(context, it.data?.data!!) {
                        toast.value = backupCompleted
                    }.let { Preferences.setLastBackupTime() }
                }
            }
        }
    }

    DropdownMenu(expanded = displayMenu.value, onDismissRequest = { displayMenu.value = false }) {
        // Creating dropdown menu item, on click
        // would create a Toast message
        DropdownMenuItem(enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_settings)) },
            onClick = {
                displayMenu.value = false
                IntentManager.startPreferencesActivity(context)
            })
        DropdownMenuItem(text = { Text(text = stringResource(id = R.string.action_bar_help)) },
            onClick = {
                displayMenu.value = false
                IntentManager.startHelpScreen(context)
            })
        DropdownMenuItem(enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_change_master_password)) },
            onClick = {
                displayMenu.value = false
                showChangePasswordDialog.value = true
            })
        IntentManager.getMenuItems(DropDownMenu.TopActionBarMenu).forEach {
            DropdownMenuItem(text = { Text(text = stringResource(id = it.first)) }, onClick = {
                displayMenu.value = false
                try {
                    it.second(context)
                } catch (ex: Exception) {
                    Log.e(TAG, "Plugin failed to do the menu", ex)
                }
            })
        }
        DropdownMenuItem(enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_import_export)) },
            onClick = {
                exportImport.value = true
            })
        DropdownMenu(expanded = exportImport.value,
            onDismissRequest = { exportImport.value = false }) {
            DropdownMenuItem(text = { Text(text = stringResource(id = R.string.action_bar_backup)) },
                onClick = {
                    displayMenu.value = false
                    backupDocumentSelectedResult.launch(
                        ExportConfig.getCreateDocumentIntent()
                    )
                })
            // Currently does not work from login screen
            // TODO: Make work from login screen?
            DropdownMenuItem(text = { Text(text = stringResource(id = R.string.action_bar_restore)) },
                onClick = {
                    try {
                        displayMenu.value = false
                        selectRestoreDocumentLauncher.launch(
                            ExportConfig.getOpenDocumentIntent()
                        )
                    } catch (ex: ActivityNotFoundException) {
                        Log.e(TAG, "Cannot launch ACTION_OPEN_DOCUMENT")
                    }
                })
            DropdownMenuItem(text = { Text(text = stringResource(id = R.string.action_bar_import_google_passwordmanager)) },
                onClick = {
                    try {
                        displayMenu.value = false
                        ImportGooglePasswords.startMe(context)
                    } catch (ex: ActivityNotFoundException) {
                        Log.e(TAG, "Cannot launch ImportGooglePasswordManager", ex)
                    }
                })
            IntentManager.getMenuItems(DropDownMenu.TopActionBarImportExportMenu).forEach {
                DropdownMenuItem(text = { Text(text = stringResource(id = it.first)) }, onClick = {
                    displayMenu.value = false
                    try {
                        coroutineScope.launch {
                            it.second(context)
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Plugin failed to do the menu", ex)
                    }
                })
            }
        }
    }
}

// TODO: Wrong place
private suspend fun initiateBackup(
    context: Context,
    uri: Uri,
    completed: () -> Unit,
) {
    BackupDatabase.backup().let { accumulatedStringBuilder: StringBuilder ->
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(accumulatedStringBuilder.toString().toByteArray())
        }
    }
    completed()
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SafeTheme {
        Column {
            TopActionBar({}, false)
            HorizontalDivider(modifier = Modifier.padding(10.dp))
            TopActionBar({}, true)
        }
    }
}
