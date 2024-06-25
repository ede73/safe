package fi.iki.ede.safe.gpm.ui.composables

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.debug
import fi.iki.ede.safe.R
import fi.iki.ede.safe.gpm.ui.activities.readAndParseCSV
import fi.iki.ede.safe.gpm.ui.models.DeleteImportReminder
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import fi.iki.ede.safe.ui.utilities.firebaseTry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(
    avertInactivity: ((Context, String) -> Unit)?,
    hasUnlinkedItemsFromPreviousRound: Boolean,
    done: () -> Unit,
    skipImportReminder: Boolean = false
) {
    val context = LocalContext.current
    val myScope = CoroutineScope(Dispatchers.IO)
    val importFailed = stringResource(id = R.string.google_password_import_failed)

    val deleteImportReminder =
        remember { mutableStateOf(DeleteImportReminder.NO_DOCUMENT_SELECTED) }
    val finishLinking = remember { mutableStateOf(false) }
    val allowAcceptAndMove = remember { mutableStateOf(false) }
    //val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(makeFakeImport()) }
    val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(null) }
    val message = remember { mutableStateOf("") }
    val showDialog = remember { mutableStateOf(false) }
    val showUsage = remember { mutableStateOf(true) }
    val title = remember { mutableStateOf("Import") }
    var selectedDoc by remember { mutableStateOf<Uri?>(null) }
    var showDocOrInfo by remember { mutableStateOf("") }

    val selectDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedDoc = result.data!!.data!!
                showDocOrInfo = result.data!!.data!!.toString()
            }
        }

    fun isItSafeToExit(): Boolean {
        if (selectedDoc !== null && deleteImportReminder.value != DeleteImportReminder.DOCUMENT_SELECTED_REMINDED_OF_DELETION) {
            deleteImportReminder.value =
                DeleteImportReminder.DOCUMENT_SELECTED_REMIND_DELETION
            return false
        } else {
            return true
        }
    }

    if (deleteImportReminder.value == DeleteImportReminder.DOCUMENT_SELECTED_REMIND_DELETION) {
        UsageInfo(
            stringResource(id = R.string.google_password_import_delete_reminder),
            onDismiss = {
                deleteImportReminder.value =
                    DeleteImportReminder.DOCUMENT_SELECTED_REMINDED_OF_DELETION
            })
    }
    if (hasUnlinkedItemsFromPreviousRound) {
        finishLinking.value = true
    }

    if (showDialog.value) {
        MyProgressDialog(showDialog, title, message)
    }

    Column(Modifier.padding(12.dp)) {
        Text(
            showDocOrInfo.ifEmpty {
                stringResource(id = R.string.google_password_import_select_doc)
            }
        )
        Row {
            SafeTextButton(onClick = {
                selectDocument.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                // doesn't work on android8(O/24), nor 9(P/26)
                                // on S24(UPSIDE_DOWN_CAKE/34) this works nice,
                                it.setType("text/comma-separated-values")
                            } else {
                                it.setType("*/*")
                            }
                            it
                        }
                )
            }) {
                Text(stringResource(id = R.string.google_password_import_select))
            }

            SafeTextButton(
                enabled = selectedDoc != null,
                onClick = {
                    showDialog.value = true
                    allowAcceptAndMove.value = false
                    context.contentResolver.openInputStream(selectedDoc!!)
                        ?.let {
                            myScope.launch {
                                readAndParseCSV(it, importChangeSet, complete = {
                                    showDialog.value = false
                                    allowAcceptAndMove.value = true
                                }) {
                                    message.value = it
                                    println(it)
                                    if (avertInactivity != null) {
                                        avertInactivity(context, "GPM Import")
                                    }
                                }
                            }
                        } ?: run {
                        selectedDoc = null
                        showDocOrInfo = importFailed
                    }
                }) {
                Text(stringResource(id = R.string.google_password_import_import))
            }
        }

        SafeTextButton(
            enabled = selectedDoc != null,
            onClick = {
                firebaseTry("Delete input document") {
                    val document = DocumentFile.fromSingleUri(context, selectedDoc!!)
                    document?.delete()
                    deleteImportReminder.value =
                        DeleteImportReminder.DOCUMENT_SELECTED_REMINDED_OF_DELETION
                    selectedDoc = null
                    showDocOrInfo = ""
                }.firebaseCatch {
                    firebaseRecordException("Failed to delete input document", it)
                }
            }) {
            Text(stringResource(id = R.string.google_password_import_delete))
        }
        SafeTextButton(onClick = {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
            context.startActivity(intent)
        }) {
            Text(stringResource(id = R.string.google_password_launch_gpm))
        }

        SafeButton(
            enabled = allowAcceptAndMove.value,
            onClick = {
                if (isItSafeToExit()) {
                    myScope.launch {
                        withContext(Dispatchers.IO) {
                            if (importChangeSet.value != null) {
                                doImport(importChangeSet.value!!)
                                DataModel.loadFromDatabase()
                            }
                        }
                        done()
                    }
                }
            }) {
            Text(stringResource(id = R.string.google_password_import_accept))
        }

        if (finishLinking.value) {
            SafeButton(
                onClick = {
                    if (isItSafeToExit()) {
                        done()
                    }
                }) {
                Text(stringResource(id = R.string.google_password_import_finish_linking))
            }
        }
        ImportResultListPager(importChangeSet, done)
        if (!skipImportReminder && showUsage.value) {
            UsageInfo(
                stringResource(id = R.string.google_password_import_usage),
                onDismiss = { showUsage.value = false }
            )
        }
    }
}

private fun doImport(importChangeSet: ImportChangeSet) {
    val add = importChangeSet.newAddedOrUnmatchedIncomingGPMs
    // there's no point updating HASH Matches (ie. nothing has changed)
    val update =
        importChangeSet.getNonConflictingGPMs.mapNotNull { (incomingGPM, scoredMatch) ->
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
        ImportScreen(::fake, false, {}, true)
    }
}