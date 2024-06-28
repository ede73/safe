package fi.iki.ede.safe.gpm.ui.composables

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.debug
import fi.iki.ede.safe.R
import fi.iki.ede.safe.gpm.ui.activities.readAndParseCSV
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.composable.YesNoDialog
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.ZonedDateTime
import kotlin.reflect.KFunction1

@Composable
fun ImportScreen(
    avertInactivity: ((Context, String) -> Unit)?,
    hasUnlinkedItemsFromPreviousRound: Boolean,
    skipImportReminder: Boolean = false,
    _done: () -> Unit,
) {

    val context = LocalContext.current
    val myScope = CoroutineScope(Dispatchers.IO)
    val importFailed = stringResource(id = R.string.google_password_import_failed)

    var deleteAllCounter = 0
    val askToDelete = remember { mutableStateOf(false) }
    val allowAcceptAndMove = remember { mutableStateOf(false) }
    val finishLinking = remember { mutableStateOf(false) }
    val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(null) }


    val showImportProgress = remember { mutableStateOf(false) }
    val showUsage = remember {
        mutableStateOf(
            DateUtils.getPeriodBetweenDates(
                ZonedDateTime.now(),
                DateUtils.unixEpochSecondsToLocalZonedDateTime(Preferences.getGpmImportUsageShown())
            ).days > 10
        )
    }
    val allowContinuingLastImport = remember { mutableStateOf(true) }

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
                getImportStreamAndMoveOrDeleteOriginal(
                    context,
                    result.data!!.data!!,
                )?.let { inputStream ->
                    allowContinuingLastImport.value = false
                    showImportProgress.value = true
                    allowAcceptAndMove.value = false
                    actuallyImport(
                        context,
                        inputStream,
                        myScope,
                        ::addMessageToFlow,
                        importChangeSet,
                        avertInactivity,
                        complete = { success ->
                            if (success) {
                                allowAcceptAndMove.value = true
                            } else {
                                allowAcceptAndMove.value = false
                                addMessageToFlow(importFailed)
                            }
                        }
                    )
                }
            }
        }

    fun done() {
        if (doesInternalDocumentExist(context)) {
            askToDelete.value = true
        } else
            _done()
    }

    if (BuildConfig.DEBUG && allowContinuingLastImport.value) {
        fun cont() {
            getInternalDocumentInputStream(context)?.let { inputStream ->
                allowContinuingLastImport.value = false
                showImportProgress.value = true
                allowAcceptAndMove.value = false
                actuallyImport(
                    context,
                    inputStream,
                    myScope,
                    ::addMessageToFlow,
                    importChangeSet,
                    avertInactivity,
                    complete = { success ->
                        if (success) {
                            allowAcceptAndMove.value = true
                        } else {
                            allowAcceptAndMove.value = false
                            addMessageToFlow(importFailed)
                        }
                    }
                )
            }
        }
        YesNoDialog(
            allowContinuingLastImport,
            title = "Internal copy found, continue import?",
            positiveText = "Continue",
            positive = { cont() },
            negativeText = "Don't continue"
        )
    }

    if (askToDelete.value) {
        YesNoDialog(
            askToDelete,
            title = "Delete internal copy?",
            positiveText = "Delete",
            positive = { deleteInternalCopy(context) },
            negativeText = "Keep", dismissed = { _done() }
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
            allowAcceptAndMove
        )
    }

    Column(Modifier.padding(12.dp)) {
        Text(stringResource(id = R.string.google_password_import_select_doc),
            modifier = Modifier.clickable {
                deleteAllCounter++
                if (deleteAllCounter > 7) {
                    myScope.launch {
                        DataModel.deleteAllSavedGPMs()
                    }
                }
            }
        )
        Row {
            SafeTextButton(onClick = {
                val downloadsUri: Uri = Uri.parse(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .toString()
                )
                selectGpmCsvExport.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
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

        SafeButton(enabled = allowAcceptAndMove.value, onClick = {
            myScope.launch {
                withContext(Dispatchers.IO) {
                    if (importChangeSet.value != null) {
                        showImportProgress.value = true
                        addMessageToFlow("Import to DB...")
                        doImport(importChangeSet.value!!)
                        addMessageToFlow("Reload datamodel...")
                        DataModel.loadFromDatabase()
                        showImportProgress.value = false
                    }
                }
                done()
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
            SafeButton(onClick = { _done() }) {
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

        ImportResultListPager(importChangeSet, _done)

        if (!skipImportReminder && showUsage.value) {
            UsageInfo(stringResource(id = R.string.google_password_import_usage),
                onDismiss = {
                    Preferences.gpmImportUsageShown()
                    showUsage.value = false
                })
        }
    }
}

fun actuallyImport(
    context: Context,
    inputStream: InputStream,
    myScope: CoroutineScope,
    addMessageToFlow: KFunction1<String, Unit>,
    importChangeSet: MutableState<ImportChangeSet?>,
    avertInactivity: ((Context, String) -> Unit)?,
    complete: (Boolean) -> Unit
) {
    myScope.launch {
        readAndParseCSV(inputStream, importChangeSet, complete = complete) {
            addMessageToFlow(it)
            if (avertInactivity != null) {
                avertInactivity(context, "GPM Import")
            }
        }
    }
}

// Google Password Manager exports stuff in plain text (*puke*)
// let's give user a chance to delete the export immediately
// or move to our app folder (which android OS keeps quite safe)
fun getImportStreamAndMoveOrDeleteOriginal(
    context: Context,
    selectedDoc: Uri
): InputStream? = copyImportToMyAppFolder(context, selectedDoc) ?: run {
    // oh, we failed to copy..not much we can do here anymore?
    // Read the INPUT and ask to delete
    val i = context.contentResolver.openInputStream(selectedDoc)
    i?.let { ByteArrayInputStream(it.readBytes()) }
}.apply {
    val document = DocumentFile.fromSingleUri(context, selectedDoc)
    document?.delete()
}

private const val importedDocFile = "import.csv"
private fun doesInternalDocumentExist(context: Context) =
    File(context.filesDir, importedDocFile).exists()

private fun deleteInternalCopy(context: Context) =
    File(context.filesDir, importedDocFile).let {
        if (it.exists()) {
            it.delete()
        }
    }

private fun getInternalDocumentInputStream(context: Context): InputStream? {
    val file = File(context.filesDir, importedDocFile)
    if (file.exists()) {
        return file.inputStream()
    } else {
        return null
    }
}

private fun copyImportToMyAppFolder(context: Context, sourceUri: Uri): InputStream? =
    try {
        val resolver = context.contentResolver
        val destinationFile = File(context.filesDir, importedDocFile)
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        val copiedFiled = File(context.filesDir, importedDocFile)
        // in emulator we will keep this file in app folder
        if (!BuildConfig.DEBUG) {
            copiedFiled.deleteOnExit()
        }
        copiedFiled.inputStream()
    } catch (ex: Exception) {
        val i = context.contentResolver.openInputStream(sourceUri)
        i?.let { ByteArrayInputStream(it.readBytes()) }
    }


private fun doImport(importChangeSet: ImportChangeSet) {
    val add = importChangeSet.newAddedOrUnmatchedIncomingGPMs
    // there's no point updating HASH Matches (ie. nothing has changed)
    val update = importChangeSet.getNonConflictingGPMs.mapNotNull { (incomingGPM, scoredMatch) ->
        if (!scoredMatch.hashMatch) incomingGPM to scoredMatch.item else null
    }.toMap()
    val delete = importChangeSet.nonMatchingSavedGPMsToDelete

    debug {
        println("ADD ${add.size} entries")
        println("UPDATE ${update.size} entries")
        println("DELETE ${delete.size} entries")
    }
    // There must be no overlap between ones we delete/once we get in - of course we can't test this
    //assert(delete.intersect(add).size == 0)
    // There must be no overlap between ones we delete/we update!
    assert(update.map { it.value }.toSet().intersect(delete).isEmpty())

    // Should be fine to run async, next screen will pick up changes when DataModel reloads
    CoroutineScope(Dispatchers.IO).launch {
        DataModel.finishGPMImport(delete, update, add)
    }
}

@Preview(showBackground = true)
@Composable
fun ImportScreenPreview() {
    SafeTheme {
        fun fake(a: Context, b: String) {}
        ImportScreen(::fake, hasUnlinkedItemsFromPreviousRound = false, true) {
        }
    }
}