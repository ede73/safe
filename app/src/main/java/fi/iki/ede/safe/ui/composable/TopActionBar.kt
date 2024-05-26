package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.Backup
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.password.ChangePassword
import fi.iki.ede.safe.ui.activities.AutoLockingComponentActivity
import fi.iki.ede.safe.ui.activities.HelpScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.ui.activities.PasswordSearchScreen
import fi.iki.ede.safe.ui.activities.PreferenceActivity
import fi.iki.ede.safe.ui.activities.RestoreScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Used in category and password list views
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopActionBar(
    onAddRequested: (addCompleted: ManagedActivityResultLauncher<Intent, ActivityResult>) -> Unit = {},
    onAddCompleted: () -> Unit = {},
) {
    val tag = "TopActionBar"
    val context = LocalContext.current
    var displayMenu by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val addCompleted = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    onAddCompleted()
                }
            }
        }
    )
    val restoreCompleted = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    // we don't really care if restoration succeed or not
                    // if it did, we've new categories and passwords
                    // if it failed, we've got the old ones
                    // User has been toasted already as well
                }
            }
        }
    )

    val selectRestoreDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    restoreCompleted.launch(
                        RestoreScreen.getIntent(context, false, it.data!!.data!!)
                    )
                }
            }
        }
    )
    val selectRestoreDocumentLauncherOld = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    restoreCompleted.launch(
                        RestoreScreen.getIntent(context, true, it.data!!.data!!)
                    )
                }
            }
        }
    )

    var toast by remember { mutableStateOf("") }

    val backupDocumentSelectedResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    if (it.data?.data != null) {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { backup(context, it) }
                        }
                    }
                }
            }
        }
    )

    if (toast != "") {
        Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
        toast = ""
    }
    TopAppBar(
        title = { Text(stringResource(id = R.string.application_name), color = Color.White) },
        //backgroundColor = Color(0xff0f9d58),
        actions = {
            IconButton(onClick = {
                onAddRequested(addCompleted)
            }, modifier = Modifier.testTag(TESTTAG_TOPACTIONBAR_ADD)) {
                Icon(Icons.Default.Add, stringResource(id = R.string.generic_add))
            }

            IconButton(onClick = {
                AutoLockingComponentActivity.lockTheApplication(context)
                LoginScreen.startMe(context, dontOpenCategoryScreenAfterLogin = true)
            }) {
                Icon(Icons.Default.Lock, stringResource(id = R.string.action_bar_lock))
            }

            IconButton(
                onClick = { PasswordSearchScreen.startMe(context) },
                modifier = Modifier.testTag(TESTTAG_TOPACTIONBAR_SEARCH)
            ) {
                Icon(Icons.Default.Search, stringResource(id = R.string.action_bar_search))
            }

            // Creating Icon button for dropdown menu
            IconButton(onClick = { displayMenu = !displayMenu }) {
                Icon(Icons.Default.MoreVert, "")
            }

            // Creating a dropdown menu
            DropdownMenu(
                expanded = displayMenu,
                onDismissRequest = { displayMenu = false }
            ) {

                // Creating dropdown menu item, on click
                // would create a Toast message
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_settings)) },
                    onClick = {
                        displayMenu = false
                        PreferenceActivity.startMe(context)
                    })
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_help)) },
                    onClick = {
                        displayMenu = false
                        HelpScreen.startMe(context)
                    })
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_backup)) },
                    onClick = {
                        displayMenu = false
                        backupDocumentSelectedResult.launch(
                            ExportConfig.getCreateDocumentIntent(context)
                        )
                    })
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_restore)) },
                    onClick = {
                        try {
                            displayMenu = false
                            selectRestoreDocumentLauncher.launch(
                                ExportConfig.getOpenDocumentIntent(context)
                            )
                        } catch (ex: ActivityNotFoundException) {
                            Log.e(tag, "Cannot launch ACTION_OPEN_DOCUMENT")
                        }
                    })
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_old_restore)) },
                    onClick = {
                        displayMenu = false
                        try {
                            selectRestoreDocumentLauncherOld.launch(
                                ExportConfig.getOpenDocumentIntent(context)
                            )
                        } catch (ex: ActivityNotFoundException) {
                            Log.e(tag, "Cannot launch ACTION_OPEN_DOCUMENT")
                        }
                    })
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.action_bar_change_master_password)) },
                    onClick = {
                        displayMenu = false
                        showChangePasswordDialog = true
                    })
            }
            if (showChangePasswordDialog) {
                val passwordChanged = stringResource(id = R.string.action_bar_password_changed)
                val passwordChangeFailed =
                    stringResource(id = R.string.action_bar_password_change_failed)

                EnterNewPassword {
                    val (oldPassword, newPassword) = it
                    if (oldPassword != newPassword &&
                        !oldPassword.isEmpty() &&
                        !newPassword.isEmpty()
                    ) {
                        ChangePassword.changeMasterPassword(
                            context,
                            oldPassword,
                            newPassword
                        ) { success ->
                            // NOTICE! This isn't a UI thread!
                            coroutineScope.launch(Dispatchers.Main) {
                                if (success) {
                                    // master password successfully changed
                                    Toast.makeText(
                                        context,
                                        passwordChanged,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        passwordChangeFailed,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            showChangePasswordDialog = false
                        }
                    }
                }
            }
        }
    )
}

private fun backup(
    context: Context,
    it: ActivityResult,
) {
    val n = Backup()
    val (salt, currentEncryptedMasterKey) = DBHelperFactory.getDBHelper(context)
        .fetchSaltAndEncryptedMasterKey()
    val document = n.generate(salt, currentEncryptedMasterKey)
    val outputStream = context.contentResolver.openOutputStream(it.data?.data!!, "wt")
    if (outputStream != null) {
        outputStream.write(document.toByteArray())
        outputStream.close()
    }
}

const val TESTTAG_TOPACTIONBAR_ADD = "topactionbar_add"
const val TESTTAG_TOPACTIONBAR_SEARCH = "topactionbar_search"

@Composable
private fun EnterNewPassword(
    onNewPassword: (oldAndNewPassword: Pair<Password, Password>) -> Unit
) {
    var oldPassword by remember { mutableStateOf(Password.getEmpty()) }
    var newPassword by remember { mutableStateOf(Password.getEmpty()) }
    val passwordMinimumLength = integerResource(id = R.integer.password_minimum_length)

    Dialog(
        onDismissRequest = { onNewPassword(Pair(Password.getEmpty(), Password.getEmpty())) },
        content = {
            Column {
                oldPassword = passwordTextField(textTip = R.string.action_bar_old_password)
                newPassword = verifiedPasswordTextField(
                    true,
                    R.string.login_password_tip,
                    R.string.login_verify_password_tip
                ) ?: Password.getEmpty()
                Button(
                    enabled = !newPassword.isEmpty() &&
                            newPassword != oldPassword &&
                            newPassword.length >= passwordMinimumLength,
                    onClick = { onNewPassword(Pair(oldPassword, newPassword)) }) {
                    Text(text = stringResource(id = R.string.action_bar_change_password_ok))
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TopActionBar({}, {})
}
