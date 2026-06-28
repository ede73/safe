package fi.iki.ede.safe.ui.activities

import android.content.Context
import fi.iki.ede.safe.R
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.backup.ExportConfig
import fi.iki.ede.backup.getOpenDocumentIntent
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.composable.AskBackupPasswordAndCommence
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.composable.setupActivityResultLauncher
import fi.iki.ede.safe.ui.models.RestoreViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import okio.source
import fi.iki.ede.safe.model.LoginHandler
import kotlinx.coroutines.CompletableDeferred
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.dateutils.toLocalDateTime
import fi.iki.ede.safe.ui.dialogs.restoreOldBackupDialog
import kotlin.time.Instant


@ExperimentalTime
class RestoreDatabaseScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    private val viewModel: RestoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context: Context = this
        setContent {
            val coroutineScope = rememberCoroutineScope()
            
            suspend fun verifyUserWantsToRestoreOldBackup(
                backupCreationTime: Instant,
                lastBackupDone: Instant
            ): Boolean {
                val result = CompletableDeferred<Boolean>()
                val days = DateUtils.getPeriodBetweenDates(backupCreationTime, lastBackupDone)

                val restoreOldBackupMessage = context.getString(
                    R.string.restore_screen_not_most_recent_backup_age,
                    days.days,
                    backupCreationTime.toLocalDateTime().toString(),
                    lastBackupDone.toLocalDateTime().toString()
                )
                val restoringOldBackupTitle = context.getString(R.string.restore_screen_not_most_recent_backup)
                val restoreAnywayText = context.getString(R.string.restore_screen_not_most_recent_backup_restore)
                val cancelRestoration = context.getString(R.string.restore_screen_not_most_recent_backup_cancel)

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
                return result.await()
            }

            var currentScreenState by remember { mutableStateOf("selectingDocument") }

            when (currentScreenState) {
                "selectingDocument" -> {
                    pauseInactivity(context, "Paused while selecting document")
                    val selectRestoreDocumentLauncher =
                        setupActivityResultLauncher(cancelled = {
                            resumeInactivity(context, "User cancelled restore selection")
                            setResult(RESULT_CANCELED)
                            firebaseLog("restoreDatabase cancel: finish()")
                            finish()
                        }) {
                            resumeInactivity(context, "User selected restore document")
                            viewModel.docUri = it.data?.data!!
                            currentScreenState = "askBackupPassword"
                        }
                    LaunchedEffect(Unit) {
                        selectRestoreDocumentLauncher.launch(
                            ExportConfig.getOpenDocumentIntent()
                        )
                    }
                }
                "askBackupPassword" -> {
                    val processedPasswords = remember { mutableIntStateOf(0) }
                    val processedCategories = remember { mutableIntStateOf(0) }
                    val processedMessage = remember { mutableStateOf("") }
                    
                    AskBackupPasswordAndCommence(
                        processedPasswords = processedPasswords,
                        processedCategories = processedCategories,
                        processedMessage = processedMessage,
                        selectedDocName = viewModel.docUri!!.toString(),
                    ) {
                        viewModel.backupPassword = it
                        currentScreenState = "restoration"
                    }
                }
                "restoration" -> {
                    val processedPasswords = remember { mutableIntStateOf(0) }
                    val processedCategories = remember { mutableIntStateOf(0) }
                    val processedMessage = remember { mutableStateOf("") }
                    
                    RestoreDatabaseComponent(
                        processedPasswords = processedPasswords,
                        processedCategories = processedCategories,
                        processedMessage = processedMessage,
                        backupPassword = viewModel.backupPassword!!,
                        backupSource = contentResolver.openInputStream(viewModel.docUri!!)!!.source(),
                        passwordLogin = { pwd -> LoginHandler.passwordLogin(context, pwd) },
                        linkSaveGPMAndSiteEntry = fi.iki.ede.gpmdatamodel.db.GPMDB::linkSaveGPMAndSiteEntry,
                        addSavedGPM = fi.iki.ede.gpmdatamodel.db.GPMDB::addSavedGPM,
                        onFinished = { restoredPasswords, ex ->
                            viewModel.backupPassword = null
                            if (ex == null) {
                                viewModel.docUri = null
                                processedMessage.value = context.getString(R.string.restore_screen_reread_database)
                                CoroutineScope(Dispatchers.IO).launch {
                                    DataModel.loadFromDatabase {
                                        GPMDataModel.loadFromDatabase()
                                    }
                                    withContext(Dispatchers.Main) {
                                        processedMessage.value = context.getString(R.string.restore_screen_done)
                                        IntentManager.startCategoryScreen(context)
                                        setResult(RESULT_OK)
                                        firebaseLog("restoreDbOk: finish()")
                                        finish()
                                    }
                                }
                            } else {
                                if (ex is CancellationException) {
                                    viewModel.docUri = null
                                    // user decided to cancel
                                    setResult(RESULT_CANCELED)
                                    firebaseLog("restoreDBCancel: finish()")
                                    finish()
                                } else {
                                    // try new password
                                    currentScreenState = "askBackupPassword"
                                }
                            }
                        },
                        verifyUserWantForOldBackup = { backupCreationTime, lastBackupDone ->
                            kotlinx.coroutines.runBlocking {
                                verifyUserWantsToRestoreOldBackup(
                                    backupCreationTime,
                                    lastBackupDone
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}