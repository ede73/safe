package fi.iki.ede.safe.ui.activities

import android.content.Context
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
import fi.iki.ede.backup.ExportConfig
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.composable.AskBackupPasswordAndCommence
import fi.iki.ede.safe.ui.composable.RestoreDatabaseComponent
import fi.iki.ede.safe.ui.composable.setupActivityResultLauncher
import fi.iki.ede.safe.ui.models.RestoreViewModel
import fi.iki.ede.statemachine.MainStateMachine.Companion.INITIAL
import fi.iki.ede.statemachine.StateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException


class RestoreDatabaseScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    private val viewModel: RestoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context: Context = this
        setContent {
            val coroutineScope = rememberCoroutineScope()

            StateMachine.Create("selectingDocument") {
                pauseInactivity(context, "Paused while selecting document")
                StateEvent("selectingDocument", INITIAL) {
                    var nextState by remember { mutableStateOf(false) }

                    if (!nextState) {
                        val selectRestoreDocumentLauncher =
                            setupActivityResultLauncher(cancelled = {
                                resumeInactivity(context, "User cancelled restore selection")
                                setResult(RESULT_CANCELED)
                                finish()
                            }) {
                                resumeInactivity(context, "User selected restore document")
                                viewModel.docUri = it.data?.data!!
                                nextState = true
                            }
                        LaunchedEffect(Unit) {
                            selectRestoreDocumentLauncher.launch(
                                ExportConfig.getOpenDocumentIntent()
                            )
                        }
                    } else {
                        TransitionTo("askBackupPassword")
                    }
                }
                StateEvent("askBackupPassword", INITIAL) {
                    val processedPasswords = remember { mutableIntStateOf(0) }
                    val processedCategories = remember { mutableIntStateOf(0) }
                    val processedMessage = remember { mutableStateOf("") }
                    var nextState by remember { mutableStateOf(false) }
                    if (nextState) {
                        TransitionTo("restoration")
                    } else {
                        AskBackupPasswordAndCommence(
                            processedPasswords,
                            processedCategories,
                            processedMessage,
                            viewModel.docUri!!,
                            context,
                        ) {
                            viewModel.backupPassword = it
                            nextState = true
                        }
                    }
                }
                StateEvent("restoration", INITIAL) {
                    val processedPasswords = remember { mutableIntStateOf(0) }
                    val processedCategories = remember { mutableIntStateOf(0) }
                    val processedMessage = remember { mutableStateOf("") }
                    var restorationFailure by remember { mutableStateOf(false) }
                    if (restorationFailure) {
                        TransitionTo("askBackupPassword")
                    } else {
                        RestoreDatabaseComponent(
                            processedPasswords,
                            processedCategories,
                            processedMessage,
                            context,
                            viewModel.backupPassword!!,
                            viewModel.docUri!!,
                        ) { restoredPasswords, ex ->
                            viewModel.backupPassword = null
                            viewModel.docUri = null
                            // YES, we could have 0 passwords, but 1 category
                            if (ex == null) {
                                processedMessage.value = "Re-read database"
                                coroutineScope.launch(Dispatchers.IO) {
                                    DataModel.loadFromDatabase({
                                        GPMDataModel.loadFromDatabase()
                                    })
                                }
                                processedMessage.value = "Done!"
                                IntentManager.startCategoryScreen(context)
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                if (ex is CancellationException) {
                                    // user decided to cancel
                                    setResult(RESULT_CANCELED)
                                    finish()
                                } else {
                                    // try new password
                                    restorationFailure = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}