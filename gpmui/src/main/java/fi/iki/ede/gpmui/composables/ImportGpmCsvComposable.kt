package fi.iki.ede.gpmui.composables

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.debug
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmui.BuildConfig
import fi.iki.ede.gpmui.R
import fi.iki.ede.gpmui.dialogs.MyProgressDialog
import fi.iki.ede.gpmui.dialogs.ProgressStateHolder
import fi.iki.ede.gpmui.dialogs.UsageInfoDialog
import fi.iki.ede.gpmui.dialogs.YesNoDialog
import fi.iki.ede.gpmui.utilities.deleteInternalCopyOfGpmCsvImport
import fi.iki.ede.gpmui.utilities.doesInternalCopyOfGpmCsvImportExist
import fi.iki.ede.gpmui.utilities.getInternalCopyOfGpmCsvAsImportStreamAndDeleteOriginal
import fi.iki.ede.gpmui.utilities.getInternalCopyOfGpmCsvAsInputStream
import fi.iki.ede.gpmui.utilities.readAndParseCSVToAChangeSet
import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeTextButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.InputStream
import kotlin.reflect.KFunction1

private const val TAG = "ImportScreen"

@Composable
fun ImportGpmCsvComposable(
    avertInactivity: ((Context, String) -> Unit)?,
    hasUnlinkedItemsFromPreviousRound: Boolean,
    skipImportReminder: Boolean = false,
    finishedImporting: () -> Unit,
) {
    val context = LocalContext.current
    val myScope = CoroutineScope(Dispatchers.IO)
    val importFailed = stringResource(id = R.string.google_password_import_failed)
    var deleteAllCounter = 0
    val askToDeleteInternalCopy = remember { mutableStateOf(false) }
    val importDoneCanMoveToMatching = remember { mutableStateOf(false) }
    val finishLinking = remember { mutableStateOf(false) }
    val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(null) }
    val showImportProgress = remember { mutableStateOf(false) }
    val showUsage = remember {
        mutableStateOf(
            DateUtils.getPeriodBetweenDates(
                Clock.System.now(),
                DateUtils.unixEpochSecondsToInstant(Preferences.getGpmImportUsageShown())
            ).days > 10
        )
    }
    val allowContinuingLastImport = remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        // Let's make sure if user navigates away, we'll delete temporary copy
        onDispose {
            deleteInternalCopyOfGpmCsvImport(context)
        }
    }

    fun addMessageToFlow(message: String) {
        myScope.launch {
            withContext(Dispatchers.Main) {
                ProgressStateHolder.addMessage(message)
            }
        }
    }

    val selectGpmCsvExport =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                getInternalCopyOfGpmCsvAsImportStreamAndDeleteOriginal(
                    context,
                    result.data!!.data!!,
                    originalNotDeleted = {
                        // TODO: Inform user!
                        addMessageToFlow("Original file not deleted")
                        addMessageToFlow("Original file not deleted")
                    }
                )?.let { inputStream ->
                    allowContinuingLastImport.value = false
                    showImportProgress.value = true
                    importDoneCanMoveToMatching.value = false
                    launchImportGpmCsvFileToAChangeSet(
                        context,
                        inputStream,
                        myScope,
                        ::addMessageToFlow,
                        importChangeSet,
                        avertInactivity,
                        complete = { success ->
                            if (success) {
                                importDoneCanMoveToMatching.value = true
                            } else {
                                importDoneCanMoveToMatching.value = false
                                addMessageToFlow(importFailed)
                            }
                        }
                    )
                }
            }
        }

    fun importingDoneFinishAndCleanup() {
        if (BuildConfig.DEBUG && doesInternalCopyOfGpmCsvImportExist(context)) {
            askToDeleteInternalCopy.value = true
        } else {
            deleteInternalCopyOfGpmCsvImport(context)
            finishedImporting()
        }
    }

    if (BuildConfig.DEBUG && allowContinuingLastImport.value && doesInternalCopyOfGpmCsvImportExist(
            context
        )
    ) {
        fun cont() {
            getInternalCopyOfGpmCsvAsInputStream(context)?.let { inputStream ->
                allowContinuingLastImport.value = false
                showImportProgress.value = true
                importDoneCanMoveToMatching.value = false
                launchImportGpmCsvFileToAChangeSet(
                    context,
                    inputStream,
                    myScope,
                    ::addMessageToFlow,
                    importChangeSet,
                    avertInactivity,
                    complete = { success ->
                        if (success) {
                            importDoneCanMoveToMatching.value = true
                        } else {
                            importDoneCanMoveToMatching.value = false
                            addMessageToFlow(importFailed)
                        }
                    }
                )
            }
        }
        YesNoDialog(
            allowContinuingLastImport,
            title = "Internal copy found(debug build only!), continue import?",
            positiveText = "Continue",
            positive = { cont() },
            negativeText = "Don't continue"
        )
    }

    if (askToDeleteInternalCopy.value) {
        YesNoDialog(
            askToDeleteInternalCopy,
            title = "Delete internal copy?",
            positiveText = "Delete",
            positive = {
                deleteInternalCopyOfGpmCsvImport(context)
                finishedImporting()
            },
            negativeText = "Keep", dismissed = { finishedImporting() }
        )
    }

    if (hasUnlinkedItemsFromPreviousRound) {
        finishLinking.value = true
    }

    // move to import!
    if (showImportProgress.value) {
        MyProgressDialog(
            showImportProgress,
            "Import",
            importDoneCanMoveToMatching
        )
    }

    Column(Modifier.padding(12.dp)) {
        Text(
            stringResource(id = R.string.google_password_import_select_doc),
            modifier = Modifier.clickable {
                deleteAllCounter++
                if (deleteAllCounter > 7) {
                    myScope.launch {
                        GPMDataModel.deleteAllSavedGPMs()
                    }
                    deleteAllCounter = 0
                }
            }
        )
        Row {
            SafeTextButton(onClick = {
                val downloadsUri: Uri =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .toString().toUri()
                selectGpmCsvExport.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
                        .let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                // doesn't work on android8(O/24), nor 9(P/26)
                                // on S24(UPSIDE_DOWN_CAKE/34) this works nice,
                                it.setType("text/comma-separated-values")
                            } else {
                                it.setType("*/*")
                            }
                            it
                        })
            }) {
                Text(stringResource(id = R.string.google_password_import_select))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open File"
                )
            }

            SafeTextButton(onClick = {
                val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                context.startActivity(intent)
            }) {
                Text(stringResource(id = R.string.google_password_launch_gpm))
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                )
            }
        }

        SafeButton(enabled = importDoneCanMoveToMatching.value, onClick = {
            myScope.launch {
                withContext(Dispatchers.IO) {
                    if (importChangeSet.value != null) {
                        showImportProgress.value = true

                        addMessageToFlow("Import to DB...")
                        storeChangeSet(importChangeSet.value!!)

                        addMessageToFlow("Reload datamodel...")
                        DataModel.loadFromDatabase {
                            GPMDataModel.loadFromDatabase()
                        }

                        showImportProgress.value = false
                    }
                }
                importingDoneFinishAndCleanup()
            }
        }) {
            Row {
                Text(
                    stringResource(id = R.string.google_password_import_accept),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Section",
                    modifier = Modifier.weight(0.1f)
                )
            }
        }

        if (finishLinking.value) {
            SafeButton(onClick = { finishedImporting() }) {
                Row {
                    Text(
                        stringResource(id = R.string.google_password_import_finish_linking),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Section",
                        modifier = Modifier.weight(0.1f)
                    )
                }
            }
        }

        VisualizeChangeSetPager(importChangeSet, finishedImporting)

        if (!skipImportReminder && showUsage.value) {
            UsageInfoDialog(
                stringResource(id = R.string.google_password_import_usage),
                onDismiss = {
                    Preferences.gpmImportUsageShown()
                    showUsage.value = false
                })
        }
    }
}

fun launchImportGpmCsvFileToAChangeSet(
    context: Context,
    inputStream: InputStream,
    myScope: CoroutineScope,
    addMessageToFlow: KFunction1<String, Unit>,
    importChangeSet: MutableState<ImportChangeSet?>,
    avertInactivity: ((Context, String) -> Unit)?,
    complete: (Boolean) -> Unit
) {
    myScope.launch {
        readAndParseCSVToAChangeSet(
            inputStream,
            importChangeSet,
            complete = complete
        ) {
            addMessageToFlow(it)
            if (avertInactivity != null) {
                avertInactivity(context, "GPM Import")
            }
        }
        if (avertInactivity != null) {
            avertInactivity(context, "GPM Import finished")
        }
    }
}


private fun storeChangeSet(importChangeSet: ImportChangeSet) {
    val add = importChangeSet.newAddedOrUnmatchedIncomingGPMs
    // there's no point updating HASH Matches (ie. nothing has changed)
    val update = importChangeSet.getNonConflictingGPMs.mapNotNull { (incomingGPM, scoredMatch) ->
        if (!scoredMatch.hashMatch) incomingGPM to scoredMatch.item else null
    }.toMap()
    val delete = importChangeSet.nonMatchingSavedGPMsToDelete

    debug {
        Logger.d(TAG, "ADD ${add.size} entries")
        Logger.d(TAG, "UPDATE ${update.size} entries")
        Logger.d(TAG, "DELETE ${delete.size} entries")
    }
    // There must be no overlap between ones we delete/once we get in - of course we can't test this
    //assert(delete.intersect(add).size == 0)
    // There must be no overlap between ones we delete/we update!
    assert(update.map { it.value }.toSet().intersect(delete).isEmpty())

    // Should be fine to run async, next screen will pick up changes when DataModel reloads
    CoroutineScope(Dispatchers.IO).launch {
        GPMDataModel.storeNewGpmsAndReload(delete, update, add)
    }
}

@Preview(showBackground = true)
@Composable
fun ImportScreenPreview() {
    MaterialTheme {
        ImportGpmCsvComposable(
            { _, _ -> },
            hasUnlinkedItemsFromPreviousRound = false,
            true
        ) {
        }
    }
}