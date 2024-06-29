package fi.iki.ede.safe.gpm.ui.utilities

import android.util.Log
import androidx.compose.runtime.MutableState
import fi.iki.ede.gpm.changeset.ImportChangeSet
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
import fi.iki.ede.safe.gpm.ui.models.DNDObject
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.utilities.firebaseLog
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream

// Combine two lists, siteEntries and gpms
internal fun combineLists(
    siteEntries: List<DecryptableSiteEntry>,
    gpms: List<SavedGPM>
): List<CombinedListPairs> {
    val maxSize = maxOf(siteEntries.size, gpms.size)
    val combinedList = mutableListOf<CombinedListPairs>()

    for (i in 0 until maxSize) {
        combinedList.add(
            CombinedListPairs.SiteEntryToGPM(
                siteEntries.getOrNull(i),
                gpms.getOrNull(i)
            )
        )
    }

    // The matching password list is built such that every matching site entry and SavedGPM
    // will pair up. So this leads to multiple site entries -> GPMs. As matching process continues
    // we remove the matched GPMS. Evidently this leads to situation as follows:
    // SiteEntry(1) -> GPM
    // SiteEntry(1) -> null (ie. matched GPM)
    // SiteEntry(1) -> null (ie. another matched GPM)
    // And this leads the LazyColumn to throw up, as we have a non-unique row here!
    // I don't see the opposite possible, since every GPM is unique, even if we had uneven lists
    // null -> GPM(1)
    // null -> GPM(2) (another one, not the same0
    // SO let's filter out those buggy ones

    // Since the search already goes SiteEntries to GPMs, we should be sorted, but to protect against changes
    val sortedPairs = combinedList.sortedWith(compareBy<CombinedListPairs> {
        (it as CombinedListPairs.SiteEntryToGPM).siteEntry?.id
    }.thenBy {
        (it as CombinedListPairs.SiteEntryToGPM).gpm == null
    })

    return sortedPairs.filterIndexed { index, pair ->
        val currentPair = pair as? CombinedListPairs.SiteEntryToGPM
        if (currentPair?.gpm == null) {
            val prevPair = sortedPairs.getOrNull(index - 1) as? CombinedListPairs.SiteEntryToGPM
            prevPair?.siteEntry != currentPair?.siteEntry
        } else {
            true
        }
    }.sortedWith(compareBy<CombinedListPairs, String?>(nullsLast()) {
        (it as CombinedListPairs.SiteEntryToGPM).siteEntry?.cachedPlainDescription?.lowercase()
    }.thenBy {
        (it as CombinedListPairs.SiteEntryToGPM).gpm?.cachedDecryptedName ?: ""
    }).also {
        val s = it.map { (it as CombinedListPairs.SiteEntryToGPM).siteEntry }.toSet()
        val g = it.map { (it as CombinedListPairs.SiteEntryToGPM).gpm }.toSet()
    }
}

internal fun readAndParseCSV(
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

internal fun importCSV(
    file: Set<IncomingGPM>,
    successImportChangeSet: MutableState<ImportChangeSet?>,
    complete: (success: Boolean) -> Unit,
    progressReport: (progress: String) -> Unit,
) {
    firebaseLog("Import CSV")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            progressReport("Fetch last import from Database")
            val importChangeSet = ImportChangeSet(file, DataModel.savedGPMsFlow.value)
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

internal fun processIncomingGPMs(
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

fun DNDObject.dump(): String =
    "DNDObject:" +
            when (this) {
                is DNDObject.JustString -> this.string
                is DNDObject.GPM -> "${this.savedGPM.cachedDecryptedName} - ${this.savedGPM.id}"
                is DNDObject.SiteEntry -> "${this.decryptableSiteEntry.cachedPlainDescription} - ${this.decryptableSiteEntry.id}"
                is DNDObject.Spacer -> "Spacer"
            }

sealed class CombinedListPairs {
    data class SiteEntryToGPM(val siteEntry: DecryptableSiteEntry?, val gpm: SavedGPM?) :
        CombinedListPairs()
}