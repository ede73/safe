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
import fi.iki.ede.backup.RestoreDatabase
import fi.iki.ede.crypto.Password
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.dialogs.restoreOldBackupDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.source
import fi.iki.ede.preferences.Preferences
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
@ExperimentalTime
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

    @ExperimentalTime
    suspend fun verifyUserWantsToRestoreOldBackup(
        coroutineScope: CoroutineScope,
        backupCreationTime: Instant,
        lastBackupDone: Instant
    ): Boolean {
        val result = CompletableDeferred<Boolean>()
        val days =
            DateUtils.getPeriodBetweenDates(backupCreationTime, lastBackupDone)

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
                    backupSource = stream.source(),
                    userPassword = backupPassword,
                    dbHelper = dbHelper,
                    lastBackupDone = Preferences.getLastBackupTime(),
                    linkSaveGPMAndSiteEntry = GPMDB::linkSaveGPMAndSiteEntry,
                    addSavedGPM = GPMDB::addSavedGPM,
                    passwordLogin = { pwd -> LoginHandler.passwordLogin(ctx, pwd) },
                    reportProgress = { categories: Int?, passwords: Int?, message: String? ->
                        categories?.let {
                            processedCategories.intValue = it
                        }
                        passwords?.let {
                            processedPasswords.intValue = it
                        }
                        message?.let {
                            val localized = when (it) {
                                "Begin restoration" -> context.getString(R.string.restore_screen_begin_restore)
                                "Process backup" -> context.getString(R.string.restore_screen_process_backup)
                                "Finished with backup" -> context.getString(R.string.restore_screen_finished_backup)
                                "Something failed, rollback" -> context.getString(R.string.restore_screen_restore_failed)
                                "Restoring old backup" -> context.getString(R.string.restore_screen_restoring_old_backup)
                                else -> it
                            }
                            processedMessage.value = localized
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
                ex.printStackTrace()
                onFinished(0, ex)
            }
        }
    }
}
