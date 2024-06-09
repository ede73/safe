package fi.iki.ede.safe.ui.activities

import android.app.Activity
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.fetchMatchingHashes
import fi.iki.ede.gpm.changeset.findSimilarNamesWhereUsernameMatchesAndURLDomainLooksTheSame
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.changeset.printImportReport
import fi.iki.ede.gpm.changeset.processOneFieldChanges
import fi.iki.ede.gpm.changeset.resolveMatchConflicts
import fi.iki.ede.gpm.csv.readCsv
import fi.iki.ede.gpm.debug
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.ScoringConfig
import fi.iki.ede.gpm.model.decrypt
import fi.iki.ede.gpm.similarity.findSimilarity
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.composable.ImportControls
import fi.iki.ede.safe.ui.composable.ImportEntryList
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImportGPMViewModel(application: Application) : AndroidViewModel(application) {
    // LiveData to signal the UI to show loading
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _originalGPMs = mutableListOf<SavedGPM>()
    private val _displayedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    val displayedGPMs: StateFlow<List<SavedGPM>> = _displayedGPMs

    private val _originalSiteEntries = mutableListOf<DecryptableSiteEntry>()
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries

    private var requestSearchCancellation = false

    init {
        loadGPMs()
        loadSiteEntries()
    }

    private fun loadGPMs() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DBHelperFactory.getDBHelper(getApplication())
            val importedGPMs = try {
                val imports = db.fetchSavedGPMFromDB()
                // IF user restored a database in the MEAN TIME, that means our previous export
                // is now totally unreadable (since our import / doesn't change gpm imports)
                val decryptTest = imports.firstOrNull()?.decryptedName
                imports
            } catch (ex: javax.crypto.BadPaddingException) {
                Log.e("ImportTest", "BadPaddingException: delete all GPM imports")
                // sorry dude, no point keeping imports we've NO WAY ever ever reading
                CoroutineScope(Dispatchers.IO).launch {
                    db.deleteAllSavedGPMs()
                }
                emptySet()
            }
            _originalGPMs.addAll(importedGPMs)
            _displayedGPMs.value = _originalGPMs
        }
    }

    private fun loadSiteEntries() {
        _originalSiteEntries.addAll(DataModel.getPasswords())
        _displayedSiteEntries.value = _originalSiteEntries
    }

    fun applyMatchingPasswords() {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.postValue(true)
            _displayedGPMs.value = emptyList()
            _displayedSiteEntries.value = emptyList()

            _originalGPMs.forEach { gpm ->
                _originalSiteEntries.forEach { siteEntry ->
                    if (!requestSearchCancellation) {
                        if (gpm.encryptedPassword.decrypt() == siteEntry.plainPassword) {
                            _displayedGPMs.value += gpm
                            _displayedSiteEntries.value += siteEntry
                        }
                    }
                }
            }
            println("Applied passwordname match...")
            _isLoading.postValue(false)
            requestSearchCancellation = false
        }
    }

    fun clearMatchingPasswords() {
        _displayedGPMs.value = _originalGPMs
        _displayedSiteEntries.value = _originalSiteEntries
    }

    fun applyMatchingNames() {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.postValue(true)
            val threshold = 0.8
            _displayedGPMs.value = emptyList()
            _displayedSiteEntries.value = emptyList()
            _originalGPMs.forEach { gpm ->
                _originalSiteEntries.forEach { siteEntry ->
                    if (!requestSearchCancellation) {
                        val score = findSimilarity(
                            harmonizePotentialDomainName(siteEntry.plainDescription).toLowerCasedTrimmedString(),
                            harmonizePotentialDomainName(gpm.decryptedName).toLowerCasedTrimmedString()
                        )
                        if (score > threshold) {
                            _displayedGPMs.value += gpm
                            _displayedSiteEntries.value += siteEntry
                        }
                    }
                }
            }
            println("Applied name match...")
            _isLoading.postValue(false)
            requestSearchCancellation = false
        }
    }

    fun clearMatchingNames() {
        _displayedGPMs.value = _originalGPMs
        _displayedSiteEntries.value = _originalSiteEntries
    }

    fun cancelOperation() {
        requestSearchCancellation = true
        _isLoading.postValue(false)
    }
}

class ImportGooglePasswordManager : AutolockingBaseComponentActivity() {
    private val viewModel: ImportGPMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun showOnlyMatchingPasswords(show: Boolean) {
            if (show) {
                println("Apply matching passwod")
                viewModel.applyMatchingPasswords()
            } else {
                println("clry matching passwod")
                viewModel.clearMatchingPasswords()
            }
        }

        fun showOnlyMatchingNames(show: Boolean) {
            if (show) {
                println("Apply matching name")
                viewModel.applyMatchingNames()
            } else {
                println("clr matching name")
                viewModel.clearMatchingNames()
            }
        }


        importTest(this)
        setContent {
            val isLoading by viewModel.isLoading.observeAsState(false)
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    Column {
                        ImportControls(
                            viewModel,
                            isLoading,
                            searchText,
                            ::showOnlyMatchingPasswords,
                            ::showOnlyMatchingNames
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
            val importChangeSet = ImportChangeSet(file, db.fetchSavedGPMFromDB())
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
