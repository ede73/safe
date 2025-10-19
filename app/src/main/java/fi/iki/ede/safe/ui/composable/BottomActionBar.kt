package fi.iki.ede.safe.ui.composable

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.autolock.AutolockingService
import fi.iki.ede.gpmui.activities.ImportNewGpmsScreen
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.R
import fi.iki.ede.safe.SafeApplication
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.splits.DropDownMenu
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.dialogs.ShowTrashDialog
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "BottomActionBar"

@Composable
fun BottomActionBar(
    onAddRequested: () -> Unit = {},
    loginScreen: Boolean = false
) {
    val displayMenu = remember { mutableStateOf(false) }
    val exportImport = remember { mutableStateOf(false) }
    val showChangePasswordDialog = remember { mutableStateOf(false) }
    val showTrashDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current

    SafeTheme {
        BottomAppBar(
            actions = {
                if (!loginScreen) {
                    IconButton(
                        onClick = onAddRequested,
                        modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_ADD)
                    ) {
                        Icon(Icons.Default.Add, stringResource(id = R.string.generic_add))
                    }
                    IconButton(onClick = {
                        SafeApplication.lockTheApplication(context)
                        IntentManager.startLoginScreen(
                            context, openCategoryScreenAfterLogin = false
                        )
                    }, modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_LOCK)) {
                        Icon(Icons.Default.Lock, stringResource(id = R.string.action_bar_lock))
                    }

                    IconButton(
                        onClick = { IntentManager.startSiteEntrySearchScreen(context) },
                        modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_SEARCH)
                    ) {
                        Icon(Icons.Default.Search, stringResource(id = R.string.action_bar_search))
                    }

                    IconButton(
                        onClick = { displayMenu.value = !displayMenu.value },
                        modifier = Modifier.testTag(TestTag.TOP_ACTION_BAR_MENU)
                    ) {
                        Icon(Icons.Default.MoreVert, "")
                    }

                    MakeDropdownMenu(
                        loginScreen,
                        displayMenu,
                        exportImport,
                        showChangePasswordDialog,
                        showTrashDialog
                    )
                }
            },
        )
    }

    if (showChangePasswordDialog.value) {
        ShowChangeMasterPasswordDialog(showChangePasswordDialog)
    }
    if (showTrashDialog.value) {
        ShowTrashDialog(onDismiss = { showTrashDialog.value = false })
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
    showTrashDialog: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    DropdownMenu(expanded = displayMenu.value, onDismissRequest = { displayMenu.value = false }) {
        DropdownMenuItem(
            enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_settings)) },
            onClick = {
                displayMenu.value = false
                IntentManager.startPreferencesActivity(context)
            })
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.action_bar_help)) },
            onClick = {
                displayMenu.value = false
                IntentManager.startHelpScreen(context)
            })
        DropdownMenuItem(
            enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_change_master_password)) },
            onClick = {
                displayMenu.value = false
                showChangePasswordDialog.value = true
            })
        DropdownMenuItem(
            enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_show_trash)) },
            onClick = {
                displayMenu.value = false
                showTrashDialog.value = true
            })
        IntentManager.getMenuItems(DropDownMenu.TopActionBarMenu).forEach {
            DropdownMenuItem(text = { Text(text = stringResource(id = it.first)) }, onClick = {
                displayMenu.value = false
                try {
                    it.second(context)
                } catch (ex: Exception) {
                    Logger.e(TAG, "Plugin failed to do the menu", ex)
                }
            })
        }
        DropdownMenuItem(
            enabled = !loginScreen,
            text = { Text(text = stringResource(id = R.string.action_bar_import_export)) },
            onClick = {
                exportImport.value = true
            })
        DropdownMenu(
            expanded = exportImport.value,
            onDismissRequest = { exportImport.value = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.action_bar_backup)) },
                onClick = {
                    displayMenu.value = false
                    AutolockingService.sendRestartTimer(context)
                    IntentManager.startBackupDatabaseScreen(context)
                })
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.action_bar_restore)) },
                onClick = {
                    try {
                        displayMenu.value = false
                        IntentManager.startRestoreDatabaseScreen(context)
                    } catch (ex: ActivityNotFoundException) {
                        Logger.e(TAG, "Cannot launch ACTION_OPEN_DOCUMENT")
                    }
                })
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.action_bar_import_google_passwordmanager)) },
                onClick = {
                    try {
                        displayMenu.value = false
                        ImportNewGpmsScreen.startMe(context)
                    } catch (ex: ActivityNotFoundException) {
                        Logger.e(TAG, "Cannot launch ImportGooglePasswordManager", ex)
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
                        Logger.e(TAG, "Plugin failed to do the menu", ex)
                    }
                })
            }
        }
    }
}
