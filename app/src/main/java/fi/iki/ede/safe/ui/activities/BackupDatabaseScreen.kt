package fi.iki.ede.safe.ui.activities

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.BackupDatabase
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.composable.setupActivityResultLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupDatabaseScreen : AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val toast = remember { mutableStateOf("") }
            if (toast.value != "") {
                Toast.makeText(context, toast.value, Toast.LENGTH_LONG).show()
                toast.value = ""
            }
            BackupComposable(toast)
        }
    }
}

@Composable
fun BackupComposable(toast: MutableState<String>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupCompleted = stringResource(id = R.string.action_bar_backup_completed)

    val backupDocumentSelectedResult = setupActivityResultLauncher {
        if (it.data?.data != null) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    initiateBackup(context, it.data?.data!!) {
                        toast.value = backupCompleted
                        (context as? Activity)?.finish()
                    }.let { Preferences.setLastBackupTime() }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        backupDocumentSelectedResult.launch(
            ExportConfig.getCreateDocumentIntent()
        )
    }
}

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

