package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.db.DBHelper
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.ui.activities.nudepwd
import fi.iki.ede.safe.ui.activities.reconvertDatabase
import okio.Path.Companion.toPath
import kotlin.time.ExperimentalTime

// only used in debug mode!
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
internal fun CopyDatabase(
    createDocumentLauncher: ActivityResultLauncher<String>?,
    updateOutput: (String) -> Unit
) {
    if (!BuildConfig.DEBUG) return
    val context: Context = LocalContext.current
    val dbInput =
        remember {
            mutableStateOf(
                context.getDatabasePath(DBHelper.DATABASE_NAME)?.path ?: "unknown"
            )
        }
    val dbOutput =
        remember {
            mutableStateOf(
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                        ?: ""
                }.getOrDefault("")
            )
        }

    Column {
        Text("Input")
        TextField(value = dbInput.value, onValueChange = {
            dbInput.value = it
            updateOutput(it)
        })

        Text("Output")
        TextField(value = dbOutput.value, onValueChange = { dbOutput.value = it })
        val text by remember { mutableStateOf("Copy") }

        Button(onClick = {
            createDocumentLauncher?.launch(dbInput.value.toPath().name)
        }) {
            Text(text)
        }

        var pwd by remember { mutableStateOf("12345678") }
        Row {
            TextField(value = pwd, onValueChange = { pwd = it })
            Button(onClick = {
                reconvertDatabase(pwd) {
                    Toast.makeText(context, "Reset", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Reset DB Password")
            }
        }

        var changePassword by remember { mutableStateOf(false) }

        if (changePassword) {
            EnterNewMasterPassword {
                val (oldMasterPassword, newMasterPassword) = it
                ChangeMasterKeyAndPassword.changeMasterPassword(
                    oldMasterPassword,
                    newMasterPassword
                ) { success ->
                    changePassword = false
                }
            }
        }

        Button(onClick = {
            changePassword = true
        }) {
            Text("Change password")
        }

        val res = nudepwd()
        Text(res)
    }
}