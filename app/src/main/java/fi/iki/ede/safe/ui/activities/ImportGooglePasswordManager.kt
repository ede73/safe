package fi.iki.ede.safe.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.fetchMatchingHashes
import fi.iki.ede.gpm.changeset.findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame
import fi.iki.ede.gpm.changeset.printImportReport
import fi.iki.ede.gpm.changeset.processOneFieldChanges
import fi.iki.ede.gpm.changeset.resolveMatchConflicts
import fi.iki.ede.gpm.csv.readCsv
import fi.iki.ede.gpm.debug
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.ScoringConfig
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.ui.composable.ImportControls
import fi.iki.ede.safe.ui.composable.ImportEntryList
import fi.iki.ede.safe.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImportGooglePasswordManager : AutolockingBaseComponentActivity() {
    private val viewModel: ImportGPMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        importTest(this)
        setContent {
            val isLoading by viewModel.isWorking.observeAsState(false to null as Float?)
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    Column {
                        ImportControls(
                            viewModel,
                            isLoading,
                        )
                        ImportEntryList(viewModel)
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, ImportGooglePasswordManager::class.java))
        }
    }
}

fun importTest(activity: Activity) {
    val inputPath = "a"
    val update = false
    if (update) {
        try {
            val file = activity.openFileInput(inputPath).use { inputStream ->
                readCsv(inputStream)
            }
            val db = DBHelperFactory.getDBHelper(activity.applicationContext)

            launchImport(db, file)
        } catch (ex: Exception) {
            Log.e("ImportTest", "CSV read failed", ex)
        }
    }
}

private fun launchImport(
    db: DBHelper,
    file: Set<IncomingGPM>
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val importChangeSet = ImportChangeSet(file, db.fetchSavedGPMsFromDB())
            val scoringConfig = ScoringConfig()

            processIncomingGPMs(
                importChangeSet,
                scoringConfig
            )

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
            db.deleteObsoleteSavedGPMs(delete)
            db.updateSavedGPMByIncomingGPM(update)
            db.addNewIncomingGPM(add)
        } catch (ex: Exception) {
            Log.e("ImportTest", "Import failed", ex)
        }
    }
}

private fun processIncomingGPMs(
    importChangeSet: ImportChangeSet,
    scoringConfig: ScoringConfig
) {

    debug {
        println("We have previous ${importChangeSet.getUnprocessedSavedGPMs.size} imports")
        //importChangeSet.getUnprocessedSavedGPMs.forEach { println("$it") }
        println("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} imports")
        //importChangeSet.getUnprocessedIncomingGPMs.forEach { println("$it") }
    }

    importChangeSet.matchingGPMs.addAll(fetchMatchingHashes(importChangeSet))

    if (importChangeSet.matchingGPMs.size > 0) {
        println("# filtered some(${importChangeSet.matchingGPMs.size}) away by existing hash..")
    }

    val sizeBeforeOneFields = importChangeSet.matchingGPMs.size
    processOneFieldChanges(importChangeSet, scoringConfig)

    println("We have incoming ${importChangeSet.getUnprocessedIncomingGPMs.size} new unknown passwords")
    println("We have incoming ${importChangeSet.matchingGPMs.size - sizeBeforeOneFields} 1-field-changes")

    val similarityMatchTrack = importChangeSet.matchingGPMs.size
    importChangeSet.matchingGPMs.addAll(
        findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame(
            importChangeSet,
            scoringConfig
        )
    )
    debug {
        if (importChangeSet.matchingGPMs.size - similarityMatchTrack == 0) {
            println("Similarity match yielded no result")
        }
    }

    resolveMatchConflicts(importChangeSet)

    printImportReport(importChangeSet)
}
