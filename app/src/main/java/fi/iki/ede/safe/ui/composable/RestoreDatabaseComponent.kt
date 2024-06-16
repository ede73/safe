package fi.iki.ede.safe.ui.composable

import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.RestoreDatabase
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.ui.activities.PrepareDataBaseRestorationScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

@Composable
fun RestoreDatabaseComponent(
    context: PrepareDataBaseRestorationScreen,
    backupPassword: Password,
    selectedDoc: Uri,
    onFinished: (passwords: Int, ex: Exception?) -> Unit
) {
    val stream = LocalContext.current.contentResolver.openInputStream(selectedDoc)!!
    val coroutineScope = rememberCoroutineScope()

    val restoringOldBackupTitle = stringResource(R.string.restore_screen_not_most_recent_backup)
    val restoreAnywayText = stringResource(R.string.restore_screen_not_most_recent_backup_restore)
    val cancelRestoration = stringResource(R.string.restore_screen_not_most_recent_backup_cancel)
    var processedPasswords by remember { mutableIntStateOf(0) }
    var processedCategories by remember { mutableIntStateOf(0) }
    var processedMessage by remember { mutableStateOf("") }

    Column {
        Text("Passwords $processedPasswords")
        Text("Categories $processedCategories")
        Text(processedMessage)
    }

    suspend fun verifyUserWantsToRestoreOldBackup(
        coroutineScope: CoroutineScope,
        backupCreationTime: ZonedDateTime,
        lastBackupDone: ZonedDateTime
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        val days = DateUtils.getPeriodBetweenDates(backupCreationTime, lastBackupDone)

        val restoreOldBackupMessage = context.getString(
            R.string.restore_screen_not_most_recent_backup_age,
            days.days,
            backupCreationTime.toLocalDateTime().toString(),
            lastBackupDone.toLocalDateTime().toString()
        )
        coroutineScope.launch(Dispatchers.Main) {
            AlertDialog.Builder(context)
                .setTitle(restoringOldBackupTitle)
                .setMessage(restoreOldBackupMessage)
                .setPositiveButton(restoreAnywayText) { _, _ ->
                    result.complete(true)
                }
                .setNegativeButton(cancelRestoration) { _, _ ->
                    result.complete(false)
                }
                .setOnDismissListener {
                    // Handle the case where the dialog is dismissed without an explicit action
                    result.complete(false)
                }
                .show()
        }

        // Wait for the result to be set by the dialog actions
        return result.await()
    }

    val dbHelper = DBHelperFactory.getDBHelper(LocalContext.current)

    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO + CoroutineName("DATABASE_RESTORATION")) {
            try {
                val passwords = RestoreDatabase().doRestore(
                    ctx,
                    String(stream.readBytes()),
                    backupPassword,
                    dbHelper,
                    { categories: Int?, passwords: Int?, message: String? ->
                        categories?.let {
                            processedCategories = it
                        }
                        passwords?.let {
                            processedPasswords = it
                        }
                        message?.let {
                            processedMessage = it
                        }
                    }
                ) { backupCreationTime, lastBackupDone ->
                    val restoreAnyway = runBlocking {
                        verifyUserWantsToRestoreOldBackup(
                            coroutineScope,
                            backupCreationTime,
                            lastBackupDone
                        )
                    }
                    restoreAnyway
                }
                onFinished(passwords, null)
            } catch (ex: Exception) {
                onFinished(0, ex)
            }
        }
    }
}
