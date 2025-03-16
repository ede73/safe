package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.backup.BackupDatabase
import fi.iki.ede.backup.ExportConfig
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.utilities.setBackupDueIconEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    }.let {
                        Preferences.setLastBackupTime()
                        setBackupDueIconEnabled(context, false)
                    }
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
    BackupDatabase.backup(
        DataModel.categoriesStateFlow.value,
        DataModel.softDeletedStateFlow.value,
        DataModel::getSiteEntriesOfCategory,
        /* TODO: NO NO GPMDB */
        GPMDB.fetchAllSiteEntryGPMMappings(),
        GPMDataModel.allSavedGPMsFlow.value.toSet(),
    ).let { accumulatedStringBuilder: StringBuilder ->
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(accumulatedStringBuilder.toString().toByteArray())
        }
    }
    completed()
}
