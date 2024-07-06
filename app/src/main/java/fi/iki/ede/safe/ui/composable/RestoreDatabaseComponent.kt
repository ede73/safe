package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.RestoreDatabase
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.ui.dialogs.restoreOldBackupDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

@Composable
fun RestoreDatabaseComponent(
    processedPasswords: MutableIntState,
    processedCategories: MutableIntState,
    processedMessage: MutableState<String>,
    context: Context,
    backupPassword: Password,
    selectedDoc: Uri,
    onFinished: (passwords: Int, ex: Exception?) -> Unit
) {
    val stream = LocalContext.current.contentResolver.openInputStream(selectedDoc)!!
    val coroutineScope = rememberCoroutineScope()

    val restoringOldBackupTitle = stringResource(R.string.restore_screen_not_most_recent_backup)
    val restoreAnywayText = stringResource(R.string.restore_screen_not_most_recent_backup_restore)
    val cancelRestoration = stringResource(R.string.restore_screen_not_most_recent_backup_cancel)

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
            restoreOldBackupDialog(
                context,
                restoringOldBackupTitle,
                restoreOldBackupMessage,
                restoreAnywayText,
                cancelRestoration,
                result
            )
        }

        // Wait for the result to be set by the dialog actions
        return result.await()
    }

    val dbHelper = DBHelperFactory.getDBHelper()

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
                            processedCategories.intValue = it
                        }
                        passwords?.let {
                            processedPasswords.intValue = it
                        }
                        message?.let {
                            processedMessage.value = it
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
