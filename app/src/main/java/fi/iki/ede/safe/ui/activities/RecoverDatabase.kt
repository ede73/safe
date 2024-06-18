package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.ui.composable.EnterNewMasterPassword
import fi.iki.ede.safe.ui.theme.SafeTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel


// am start -n  fi.iki.ede.safe.debug/fi.iki.ede.safe.ui.activities.RecoverDatabase
class RecoverDatabase : ComponentActivity() {
    private var output: String = ""
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            // Permission is granted. Continue the action or workflow in your app.
//        } else {
//            // Explain to the user that the feature is unavailable because the
//            // features requires a permission that the user has denied.
//        }
//    }

    private val createDocumentLauncher = registerForActivityResult(
        CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).channel.use { fileChannel ->
                    copyDatabaseToPublicFolder(fileChannel, output)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CopyDatabase(createDocumentLauncher, null) {
                output = it
            }
        }
    }

    private fun copyDatabaseToPublicFolder(dstChannel: FileChannel, output: String) {
        val dbFile = File(output)
        if (dbFile.exists()) {
            FileInputStream(dbFile).channel.use { srcChannel ->
                dstChannel.transferFrom(srcChannel, 0, srcChannel.size())
            }
        }
    }
}

@Composable
private fun CopyDatabase(
    createDocumentLauncher: ActivityResultLauncher<String>?,
    requestPermissionLauncher: ActivityResultLauncher<String>?,
    updateOutput: (String) -> Unit
) {
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
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                    ?: ""
            )
        }
    val oldPassword = remember { mutableStateOf("") }
    val newPassword = remember { mutableStateOf("") }

    Column {
        Text("Input")
        TextField(value = dbInput.value, onValueChange = {
            dbInput.value = it
            updateOutput(it)
        })

        Text("Output")
        TextField(value = dbOutput.value, onValueChange = { dbOutput.value = it })

//        var text by remember {
//            mutableStateOf(
//                when (ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE
//                )) {
//                    PackageManager.PERMISSION_GRANTED -> "Copy"
//                    else -> "Request Permission"
//                }
//            )

        var text by remember { mutableStateOf("Copy") }
        ///text = "Copy"

        Button(onClick = {
            createDocumentLauncher?.launch(File(dbInput.value).name)
//            when (ContextCompat.checkSelfPermission(
//                context,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//            )) {
//                PackageManager.PERMISSION_GRANTED ->
//                    createDocumentLauncher?.launch(dbInput.value)
//
//                else -> {
//                    requestPermissionLauncher?.launch(
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE
//                    )
//                    text = "Requesting"
//                }
//            }
        }) {
            Text(text)
        }

        Button(onClick = {
            //val db = DBHelperFactory.getDBHelper()
            Preferences.resetMasterkeyInitialized()
        }) {
            Text("Reset DB Password")
        }


        var changePassword by remember { mutableStateOf(false) }

        if (changePassword) {
            EnterNewMasterPassword {
                val (oldMasterPassword, newMasterPassword) = it
                ChangeMasterKeyAndPassword.changeMasterPassword(
                    context,
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

    }
}

@Preview(showBackground = true)
@Composable
fun RecoverDatabasePreview() {
    SafeTheme {
        CopyDatabase(null, null) {}
    }
}

