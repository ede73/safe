package fi.iki.ede.safe.gpm.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import fi.iki.ede.safe.gpm.ui.composables.ImportResultListPager
import fi.iki.ede.safe.gpm.ui.composables.ImportScreen
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import fi.iki.ede.safe.ui.utilities.firebaseLog
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream


class ImportGooglePasswords : AutolockingBaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasUnlinkedItemsFromPreviousRound =
            (DataModel._savedGPMs.filter { !it.flaggedIgnored } -
                    DataModel.siteEntryToSavedGPMStateFlow.value.values.flatten()
                        .toSet()).isNotEmpty()

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
            progressReport("Fetch last import from Database")
            val importChangeSet = ImportChangeSet(file, DataModel._savedGPMs)
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

private fun makeIncoming(name: String): IncomingGPM {
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

            ImportResultListPager(importChangeSet, {})
        }
    }
}