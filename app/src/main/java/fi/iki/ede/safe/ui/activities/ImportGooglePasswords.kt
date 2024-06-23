package fi.iki.ede.safe.ui.activities

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.changeset.fetchMatchingHashes
import fi.iki.ede.gpm.changeset.findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame
import fi.iki.ede.gpm.changeset.printImportReport
import fi.iki.ede.gpm.changeset.processOneFieldChanges
import fi.iki.ede.gpm.changeset.resolveMatchConflicts
import fi.iki.ede.gpm.csv.readCsv
import fi.iki.ede.gpm.debug
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.ScoringConfig
import fi.iki.ede.gpm.model.encrypter
import fi.iki.ede.safe.R
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTextButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import fi.iki.ede.safe.ui.utilities.firebaseLog
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import fi.iki.ede.safe.ui.utilities.firebaseTry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun MyProgressDialog(
    showDialog: MutableState<Boolean>,
    message: MutableState<String>,
    text: MutableState<String>
) {
    if (showDialog.value) {
        val messages = remember { mutableStateListOf<String>() }

        LaunchedEffect(text.value) {
            if (messages.size >= 5) {
                messages.removeAt(0)
            }
            messages.add("- ${text.value}")
        }

        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
            },
            dismissButton = {
            },
            title = {
                Text(message.value)
            },
            text = {
                Column(modifier = Modifier.padding(16.dp)) {
                    messages.forEach { message ->
                        Text(text = message)
                    }
                }
            }
        )
    }
}

@Composable
fun UsageInfo(
    message: String,
    onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = { onDismiss() },
    confirmButton = {},
    dismissButton = {
        SafeTextButton(onClick = onDismiss) {
            Text("OK")
        }
    },
    title = { Text("Important!") },
    text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(text = message)
        }
    }
)


class ImportGooglePasswords : AutolockingBaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasUnlinkedItemsFromPreviousRound =
            DBHelperFactory.getDBHelper().fetchUnprocessedSavedGPMsFromDB().isNotEmpty()

        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    ImportScreen(::avertInactivity, hasUnlinkedItemsFromPreviousRound) {
                        MergeGooglePasswordsToMine.startMe(this)
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, ImportGooglePasswords::class.java))
        }
    }
}

enum class DeleteImportReminder {
    NO_DOCUMENT_SELECTED,
    DOCUMENT_SELECTED_REMIND_DELETION,
    DOCUMENT_SELECTED_REMINDED_OF_DELETION,
}

@Composable
fun ImportScreen(
    avertInactivity: ((Context, String) -> Unit)?,
    hasUnlinkedItemsFromPreviousRound: Boolean,
    done: () -> Unit
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
            if (result.resultCode == RESULT_OK) {
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
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/comma-separated-values")
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
        ImportControls(importChangeSet, done)
        if (showUsage.value) {
            UsageInfo(
                stringResource(id = R.string.google_password_import_usage),
                onDismiss = { showUsage.value = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportControls(importChangeSet: MutableState<ImportChangeSet?>, done: () -> Unit) {
    Column {
        val pages = listOf<@Composable () -> Unit>(
            { Page1(importChangeSet) },
            { Page2(importChangeSet) },
            { Page3(importChangeSet) },
            { Page4(importChangeSet) },
        )

        val pagerState = rememberPagerState(pageCount = { 4 })

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            key = { i -> i }) { page ->
            Column(
                Modifier
                    .border(2.dp, SafeTheme.colorScheme.onSurface)
                    .shadow(3.dp, shape = RectangleShape, clip = false)
                    .fillMaxHeight()
                    .padding(10.dp),
            ) {
                pages[page].invoke()
            }
        }
    }
}

data class ItemWrapper<T>(val item: T, var isSelected: Boolean = false)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableItem(text: String, showInfo: () -> Unit, leftSpacer: Boolean = false) {
    var isSelected by remember { mutableStateOf(false) }
    Row(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { isSelected = !isSelected },
            onLongClick = { showInfo() }
        )
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Checkmark")
        }
        if (leftSpacer) {
            Spacer(modifier = Modifier.weight(0.5f))
        }
        Text(
            text = text,
        )
    }
}

@Composable
fun ShowInfo(item: SavedGPM, onDismiss: () -> Unit) {
    val dump =
        "Name: ${item.decryptedName}\nUser: ${item.decryptedUsername}\nUrl: ${item.decryptedUrl}"
    UsageInfo(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfo(item: IncomingGPM, onDismiss: () -> Unit) {
    val dump = "Name: ${item.name}\nUser: ${item.username}\nUrl: ${item.url}"
    UsageInfo(dump, onDismiss = onDismiss)
}

@Composable
fun ShowInfo(item: ScoredMatch, onDismiss: () -> Unit) {
    val dump =
        "Match:${item.matchScore}\nHashMatch:${item.hashMatch}\nName: ${item.item.decryptedName}\nUser: ${item.item.decryptedUsername}\nUrl: ${item.item.decryptedUrl}"
    UsageInfo(dump, onDismiss = onDismiss)
}

@Composable
private fun Page1(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("New passwords(will be ADDED). Ie. didn't match incoming to anything existing.")
        val selectableList =
            importChangeSet.value?.newAddedOrUnmatchedIncomingGPMs?.toList() ?: emptyList()
        val wrappedList = selectableList.map { ItemWrapper(it) }
        val showInfo = remember { mutableStateOf<IncomingGPM?>(null) }
        if (showInfo.value != null) {
            ShowInfo(showInfo.value!!, onDismiss = { showInfo.value = null })
        }
        LazyColumn {
            items(wrappedList) { entry ->
                SafeListItem { SelectableItem(entry.item.name, { showInfo.value = entry.item }) }
            }
        }
    }

@Composable
private fun Page2(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Matching passwords(will be UPDATED). Ie. matched incoming to existing.")
        val selectableList =
            importChangeSet.value?.getNonConflictingGPMs?.toList() ?: emptyList()
        val wrappedList = selectableList.map { ItemWrapper(it) }
        val showInfo = remember { mutableStateOf<IncomingGPM?>(null) }
        if (showInfo.value != null) {
            ShowInfo(showInfo.value!!, onDismiss = { showInfo.value = null })
        }
        LazyColumn {
            items(wrappedList) { entry ->
                SafeListItem {
                    SelectableItem(entry.item.first.name, { showInfo.value = entry.item.first })
                }
            }
        }
    }

@Composable
private fun Page3(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Conflicts - incoming matches multiple existing ones. We'll do nothing to sort these. They're included in other sets though.")
        Column {
            val selectableList =
                importChangeSet.value?.getMatchingConflicts?.toList() ?: emptyList()
            val wrappedList = selectableList.map {
                Pair(
                    ItemWrapper(it.first),
                    it.second.map { element -> ItemWrapper(element) }.toSet()
                )
            }

            HorizontalDivider()
            Row {
                Text("Incoming GPM")
                Spacer(modifier = Modifier.weight(0.5f))
                Text("Existing GPMs")
            }
            val showInfo1 = remember { mutableStateOf<IncomingGPM?>(null) }
            if (showInfo1.value != null) {
                ShowInfo(showInfo1.value!!, onDismiss = { showInfo1.value = null })
            }
            val showInfo2 = remember { mutableStateOf<ScoredMatch?>(null) }
            if (showInfo2.value != null) {
                ShowInfo(showInfo2.value!!, onDismiss = { showInfo2.value = null })
            }
            LazyColumn {
                wrappedList.forEach { entry ->
                    item {
                        SafeListItem {
                            SelectableItem(
                                entry.first.item.name, { showInfo1.value = entry.first.item })
                        }
                    }
                    items(entry.second.toList()) { value ->
                        SafeListItem {
                            SelectableItem(
                                value.item.item.decryptedName, { showInfo2.value = value.item },
                                true
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
private fun Page4(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Obsolete passwords(will be DELETED). Ie. identified not to exist in new import.")
        val selectableList =
            importChangeSet.value?.nonMatchingSavedGPMsToDelete?.toList() ?: emptyList()
        val wrappedList = selectableList.map { ItemWrapper(it) }
        val showInfo = remember { mutableStateOf<SavedGPM?>(null) }
        if (showInfo.value != null) {
            ShowInfo(showInfo.value!!, onDismiss = { showInfo.value = null })
        }
        LazyColumn {
            items(wrappedList) { entry ->
                SafeListItem {
                    SelectableItem(entry.item.decryptedName, { showInfo.value = entry.item })
                }
            }
        }
    }


fun readAndParseCSV(
    inputStream: InputStream,
    importChangeSet: MutableState<ImportChangeSet?>,
    complete: (success: Boolean) -> Unit,
    progressReport: (progress: String) -> Unit,
) {
    firebaseLog("Read CSV")
    try {
        progressReport("Parse CSV")
        val incomingGPMs = readCsv(inputStream)
        return importCSV(incomingGPMs, importChangeSet, complete, progressReport)
    } catch (ex: Exception) {
        firebaseRecordException("Failed to import", ex)
    }
}

private fun importCSV(
    file: Set<IncomingGPM>,
    successImportChangeSet: MutableState<ImportChangeSet?>,
    complete: (success: Boolean) -> Unit,
    progressReport: (progress: String) -> Unit,
) {
    firebaseLog("Import CSV")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val db = DBHelperFactory.getDBHelper()
            progressReport("Fetch last import from Database")
            val importChangeSet = ImportChangeSet(file, db.fetchSavedGPMsFromDB())
            val scoringConfig = ScoringConfig()

            successImportChangeSet.value = processIncomingGPMs(
                importChangeSet,
                scoringConfig,
                progressReport
            )
            progressReport("Complete!")
            complete(true)
        } catch (ex: Exception) {
            Log.e("ImportTest", "Import failed", ex)
            successImportChangeSet.value = null
            complete(false)
        }
    }
}

private fun processIncomingGPMs(
    importChangeSet: ImportChangeSet,
    scoringConfig: ScoringConfig,
    progressReport: (progress: String) -> Unit
): ImportChangeSet {

    debug {
        println("We have previous ${importChangeSet.getUnprocessedSavedGPMs.size} imports")
        //importChangeSet.getUnprocessedSavedGPMs.forEach { println("$it") }
        println("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} imports")
        //importChangeSet.getUnprocessedIncomingGPMs.forEach { println("$it") }
    }

    progressReport("Add all matching passwords by hashes")
    importChangeSet.matchingGPMs.addAll(fetchMatchingHashes(importChangeSet))

    if (importChangeSet.matchingGPMs.size > 0) {
        println("# filtered some(${importChangeSet.matchingGPMs.size}) away by existing hash..")
    }

    // TAKES FOR EVER
    progressReport("Find all entries with 1 field change (takes long time!)")
    val sizeBeforeOneFields = importChangeSet.matchingGPMs.size
    processOneFieldChanges(importChangeSet, scoringConfig, progressReport)

    println("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} new unknown passwords")
    println("We have incoming ${importChangeSet.matchingGPMs.size - sizeBeforeOneFields} 1-field-changes")

    progressReport("Do similarity match")
    val similarityMatchTrack = importChangeSet.matchingGPMs.size
    importChangeSet.matchingGPMs.addAll(
        findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame(
            importChangeSet,
            scoringConfig,
            progressReport
        )
    )
    debug {
        if (importChangeSet.matchingGPMs.size - similarityMatchTrack == 0) {
            println("Similarity match yielded no result")
        }
    }

    progressReport("Resolve(try to) all conflicts")
    resolveMatchConflicts(importChangeSet)

    printImportReport(importChangeSet)
    return importChangeSet
}

private fun doImport(importChangeSet: ImportChangeSet) {
    val db = DBHelperFactory.getDBHelper()
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
    // TODO: shouldn't access directly
    db.deleteObsoleteSavedGPMs(delete)
    db.updateSavedGPMByIncomingGPM(update)
    db.addNewIncomingGPM(add)
}

fun makeFakeImport(): ImportChangeSet {
    val incoming = setOf<IncomingGPM>(
        makeIncoming("Incoming1"),
        makeIncoming("Incoming2"),
        makeIncoming("Incoming3"),
        makeIncoming("Incoming4"),
        makeIncoming("Incoming5"),
        makeIncoming("Incoming6"),
        makeIncoming("Incoming7"),
        makeIncoming("Incoming8"),
        makeIncoming("Incoming9"),
        makeIncoming("Incoming10"),
        makeIncoming("Incoming11"),
        makeIncoming("Incoming12"),
        makeIncoming("Incoming13"),
        makeIncoming("Incoming14"),
        makeIncoming("Incoming15"),
    )
    val saved = setOf<SavedGPM>(
        makeSaved(1, "Saved1"),
        makeSaved(2, "Saved2"),
        makeSaved(2, "Saved3"),
        makeSaved(2, "Saved4"),
        makeSaved(2, "Saved5"),
        makeSaved(2, "Saved6"),
        makeSaved(2, "Saved7"),
        makeSaved(2, "Saved8"),
        makeSaved(2, "Saved9"),
        makeSaved(2, "Saved10"),
        makeSaved(2, "Saved11"),
        makeSaved(2, "Saved12"),
    )
    val incomingAndConflict = makeIncoming("Incoming3")
    val a = incomingAndConflict to ScoredMatch(0.5, makeSaved(3, "Saved3"), true)
    val b = makeIncoming("Incoming4") to ScoredMatch(0.7, makeSaved(4, "Saved4"), false)
    val c = incomingAndConflict to ScoredMatch(0.7, makeSaved(4, "Saved5"), false)
    val matches = mutableSetOf<Pair<IncomingGPM, ScoredMatch>>(a, b, c)
    // iterate ALL matchingGPMs ie.  overlap (passwords whose hash match perfectly or there's 1 field change)
    // and add them to map Map<IncomingGPM, Set<ScoredMatch>>
    //any incomingGPM with >1 ScoredMatch is a conflict
    return ImportChangeSet(incoming, saved, matches)

}

fun makeSaved(id: Long, name: String): SavedGPM {
    return SavedGPM.makeFromEncryptedStringFields(
        id,
        encrypter(name.toByteArray()),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        IVCipherText.getEmpty(),
        false,
        ""
    )
}

fun makeIncoming(name: String): IncomingGPM {
    return IncomingGPM.makeFromCSVImport(name, "", "", "", "")
}

@Composable
@Preview(showBackground = true)
fun ImportGooglePasswordsPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    SafeTheme {
        Column {
            ImportScreen(null, true) {}

            HorizontalDivider(modifier = Modifier.padding(20.dp))

            val incoming = setOf<IncomingGPM>(
                makeIncoming("Incoming1"),
                makeIncoming("Incoming2"),
            )
            val saved = setOf<SavedGPM>(
                makeSaved(1, "Saved1"),
                makeSaved(2, "Saved2")
            )
            val a = makeIncoming("Incoming3") to ScoredMatch(0.5, makeSaved(3, "Saved3"), true)
            val b = makeIncoming("Incoming4") to ScoredMatch(0.7, makeSaved(4, "Saved4"), false)
            val matches = mutableSetOf<Pair<IncomingGPM, ScoredMatch>>(a, b)
            val import: ImportChangeSet = ImportChangeSet(incoming, saved, matches)
            val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(import) }


            ImportControls(importChangeSet, {})
        }
    }
}