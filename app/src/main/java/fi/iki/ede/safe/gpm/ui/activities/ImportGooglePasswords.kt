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
        progressReport("Read and parse CSV")
        val incomingGPMs = readCsv(inputStream)
        progressReport("Import CSV")
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

            progressReport("Process incoming GPMs")
            successImportChangeSet.value = processIncomingGPMs(
                importChangeSet,
                scoringConfig,
                progressReport
            )

            progressReport("Complete!")
            complete(true)
        } catch (ex: Exception) {
            Log.e("ImportTest", "Import failed", ex)
            progressReport("Import failed! $ex")
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
        progressReport("We have previous ${importChangeSet.getUnprocessedSavedGPMs.size} imports")
        //importChangeSet.getUnprocessedSavedGPMs.forEach { println("$it") }
        progressReport("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} imports")
        //importChangeSet.getUnprocessedIncomingGPMs.forEach { println("$it") }
    }

    progressReport("Add all matching passwords by hashes")
    importChangeSet.matchingGPMs.addAll(fetchMatchingHashes(importChangeSet))

    if (importChangeSet.matchingGPMs.size > 0) {
        progressReport("# filtered some(${importChangeSet.matchingGPMs.size}) away by existing hash..")
    }

    // TAKES FOR EVER
    progressReport("Find all entries with 1 field change (takes long time!)")
    val sizeBeforeOneFields = importChangeSet.matchingGPMs.size
    processOneFieldChanges(importChangeSet, scoringConfig, progressReport)

    progressReport("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} new unknown passwords")
    progressReport("We have incoming ${importChangeSet.matchingGPMs.size - sizeBeforeOneFields} 1-field-changes")

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
            progressReport("Similarity match yielded no result")
        }
    }

    progressReport("Resolve(try to) all conflicts")
    resolveMatchConflicts(importChangeSet, progressReport)

    printImportReport(importChangeSet)
    return importChangeSet
}


fun makeFakeImport(): ImportChangeSet {
    val incoming = setOf<IncomingGPM>(
        makeIncomingForTesting("Incoming1"),
        makeIncomingForTesting("Incoming2"),
        makeIncomingForTesting("Incoming3"),
        makeIncomingForTesting("Incoming4"),
        makeIncomingForTesting("Incoming5"),
        makeIncomingForTesting("Incoming6"),
        makeIncomingForTesting("Incoming7"),
        makeIncomingForTesting("Incoming8"),
        makeIncomingForTesting("Incoming9"),
        makeIncomingForTesting("Incoming10"),
        makeIncomingForTesting("Incoming11"),
        makeIncomingForTesting("Incoming12"),
        makeIncomingForTesting("Incoming13"),
        makeIncomingForTesting("Incoming14"),
        makeIncomingForTesting("Incoming15"),
    )
    val saved = setOf<SavedGPM>(
        makeSavedForTesting(1, "Saved1"),
        makeSavedForTesting(2, "Saved2"),
        makeSavedForTesting(2, "Saved3"),
        makeSavedForTesting(2, "Saved4"),
        makeSavedForTesting(2, "Saved5"),
        makeSavedForTesting(2, "Saved6"),
        makeSavedForTesting(2, "Saved7"),
        makeSavedForTesting(2, "Saved8"),
        makeSavedForTesting(2, "Saved9"),
        makeSavedForTesting(2, "Saved10"),
        makeSavedForTesting(2, "Saved11"),
        makeSavedForTesting(2, "Saved12"),
    )
    val incomingAndConflict = makeIncomingForTesting("Incoming3")
    val a = incomingAndConflict to ScoredMatch(0.5, makeSavedForTesting(3, "Saved3"), true)
    val b = makeIncomingForTesting("Incoming4") to ScoredMatch(
        0.7,
        makeSavedForTesting(4, "Saved4"),
        false
    )
    val c = incomingAndConflict to ScoredMatch(0.7, makeSavedForTesting(4, "Saved5"), false)
    val matches = mutableSetOf<Pair<IncomingGPM, ScoredMatch>>(a, b, c)
    // iterate ALL matchingGPMs ie.  overlap (passwords whose hash match perfectly or there's 1 field change)
    // and add them to map Map<IncomingGPM, Set<ScoredMatch>>
    //any incomingGPM with >1 ScoredMatch is a conflict
    return ImportChangeSet(incoming, saved, matches)

}

fun makeSavedForTesting(id: Long, name: String): SavedGPM {
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

private fun makeIncomingForTesting(name: String): IncomingGPM {
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
                makeIncomingForTesting("Incoming1"),
                makeIncomingForTesting("Incoming2"),
            )
            val saved = setOf<SavedGPM>(
                makeSavedForTesting(1, "Saved1"),
                makeSavedForTesting(2, "Saved2")
            )
            val a = makeIncomingForTesting("Incoming3") to ScoredMatch(
                0.5,
                makeSavedForTesting(3, "Saved3"),
                true
            )
            val b = makeIncomingForTesting("Incoming4") to ScoredMatch(
                0.7,
                makeSavedForTesting(4, "Saved4"),
                false
            )
            val matches = mutableSetOf<Pair<IncomingGPM, ScoredMatch>>(a, b)
            val import: ImportChangeSet = ImportChangeSet(incoming, saved, matches)
            val importChangeSet = remember { mutableStateOf<ImportChangeSet?>(import) }

            ImportResultListPager(importChangeSet, {})
        }
    }
}