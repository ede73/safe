package fi.iki.ede.safe.ui.models

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.findSimilarity
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class ImportGPMViewModel(application: Application) : AndroidViewModel(application) {
    // LiveData to signal the UI to show loading
    private val _isWorking = MutableLiveData(false to null as Float?)
    val isWorking: LiveData<Pair<Boolean, Float?>> = _isWorking

    private val _originalGPMs = mutableListOf<SavedGPM>()
    private val _displayedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    val displayedGPMs: StateFlow<List<SavedGPM>> = _displayedGPMs

    private val _originalSiteEntries = mutableListOf<DecryptableSiteEntry>()
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries

    private val jobManager = JobManager { completed, percentCompleted ->
        _isWorking.postValue(completed to percentCompleted)
    }

    init {
        loadGPMs()
        loadSiteEntries()
    }

    private val removeGPMs = mutableSetOf<SavedGPM>()
    fun removeGPM(id: Long) {
        try {
            val gpm = _originalGPMs.first { it.id == id }
            _originalGPMs.removeAll { it.id == id }
            println("removeGPMs.add(gpm")
            removeGPMs.add(gpm)
            println("_displayedGPMs.update")
            _displayedGPMs.update { currentList ->
                currentList.filter { it.id != id }
            }
        } catch (ex: Exception) {
            println("remove GPM $id failed $ex")
        }
    }

    private fun loadGPMs() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DBHelperFactory.getDBHelper(getApplication())
            val importedGPMs = try {
                val imports = db.fetchUnprocessedSavedGPMsFromDB()
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

    // thread safe abstraction allowing implementing easy match algorithms
    // and the heavy lifting of editing lists while iterating them is done here
    // not messing up the call site
    private fun <O, I> launchIterateLists(
        name: String,
        outerList: List<O>,
        innerList: List<I>,
        outerDisplayList: MutableStateFlow<List<O>>,
        innerDisplayList: MutableStateFlow<List<I>>,
        start: () -> Pair<List<O>, List<I>>,
        compare: (outerItem: O, innerItem: I) -> Pair<O?, I?>,
        exceptionHandler: (Exception) -> Unit = {}
    ) = viewModelScope.launch(Dispatchers.Default) {

        val (outerDisplayInit, innerDisplayInit) = start()
        outerDisplayList.value = outerDisplayInit
        innerDisplayList.value = innerDisplayInit

        // allow iterating while editing
        val copyOfOuterList = outerList.toList()
        val copyOfInnerList = innerList.toList()

        val progress =
            Progress(copyOfOuterList.size * copyOfInnerList.size.toLong()) { percentCompleted ->
                jobManager.updateProgress(percentCompleted)
            }
        try {
            run jobCancelled@{
                if (isActive) {
                    copyOfOuterList.forEach { outerEntry ->
                        copyOfInnerList.forEach { innerEntry ->
                            if (!isActive) return@jobCancelled
                            progress.increment()
                            val (addOuterItem, addInnerItem) = compare(outerEntry, innerEntry)
                            addOuterItem?.let { outerDisplayList.value += addOuterItem }
                            addInnerItem?.let { innerDisplayList.value += addInnerItem }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            println("Something went wrong in $name, $ex")
            exceptionHandler(ex)
        } finally {
            jobManager.updateProgress(100.0f)
        }
    }

    private fun <L> launchIterateList(
        name: String,
        list: List<L>,
        displayList: MutableStateFlow<List<L>>,
        start: () -> List<L>,
        compare: (item: L) -> L?,
        exceptionHandler: (Exception) -> Unit = {}
    ) = launchIterateLists(
        name,
        list,
        listOf<L?>(null),
        displayList,
        MutableStateFlow<List<L?>>(listOf(null)),
        start = { start() to emptyList() },
        compare = { l, _ -> compare(l) to null },
        exceptionHandler
    )

    fun launchSearchMatchingPasswords() {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                launchIterateLists("applyMatchingPasswords",
                    _originalSiteEntries,
                    _originalGPMs,
                    _displayedSiteEntries,
                    _displayedGPMs,
                    start = { emptyList<DecryptableSiteEntry>() to emptyList<SavedGPM>() },
                    compare = { outerEntry, innerEntry ->
                        if (outerEntry.plainPassword == innerEntry.decryptedPassword) {
                            outerEntry to innerEntry
                        }
                        null to null
                    })
            }
        }
    }

    fun clearMatchingPasswords() {
        _displayedGPMs.value = _originalGPMs
        _displayedSiteEntries.value = _originalSiteEntries
    }

    fun launchSearchMatchingNames() {
        CoroutineScope(Dispatchers.Default).launch {
            val threshold = 0.8
            jobManager.cancelAndAddNewJob {
                launchIterateLists("applyMatchingNames",
                    _originalSiteEntries,
                    _originalGPMs,
                    _displayedSiteEntries,
                    _displayedGPMs,
                    start = { emptyList<DecryptableSiteEntry>() to emptyList<SavedGPM>() },
                    compare = { outerEntry, innerEntry ->
                        if (findSimilarity(
                                harmonizePotentialDomainName(outerEntry.plainDescription).toLowerCasedTrimmedString(),
                                harmonizePotentialDomainName(innerEntry.decryptedName).toLowerCasedTrimmedString()
                            ) > threshold
                        ) outerEntry to innerEntry
                        null to null
                    }
                )
            }
        }
    }

    fun clearMatchingNames() {
        _displayedGPMs.value = _originalGPMs
        _displayedSiteEntries.value = _originalSiteEntries
    }

    // Assuming 'scope' is your CoroutineScope, e.g., viewModelScope
    private var searchJob: Job? = null
    fun launchSearch(
        similarityThresholdOrSubString: Double,
        searchText: String,
        searchFromMyOwn: Boolean,
        searchFromBeingImported: Boolean,
    ) {
        if (searchText.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(
                similarityThresholdOrSubString,
                searchText.toLowerCasedTrimmedString(),
                searchFromMyOwn,
                searchFromBeingImported
            )
        }
    }

    private fun performSearch(
        similarityThresholdOrSubString: Double,
        searchText: LowerCaseTrimmedString,
        searchFromMyOwn: Boolean,
        searchFromBeingImported: Boolean
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJobs {
                val j1 = if (searchFromMyOwn)
                    launchIterateList(
                        "searchSiteEntries",
                        _originalSiteEntries,
                        _displayedSiteEntries,
                        start = { emptyList() },
                        compare = { item ->
                            if (similarityThresholdOrSubString > 0) {
                                if (findSimilarity(
                                        harmonizePotentialDomainName(item.plainDescription).toLowerCasedTrimmedString(),
                                        searchText
                                    ) > similarityThresholdOrSubString
                                ) item else null
                            } else if (item.plainDescription.lowercase()
                                    .contains(searchText.lowercasedTrimmed)
                            ) item else null
                        }
                    )
                else null

                val j2 = if (searchFromBeingImported)
                    launchIterateList(
                        "searchGPMs",
                        _originalGPMs,
                        _displayedGPMs,
                        start = { emptyList() },
                        compare = { item ->
                            if (similarityThresholdOrSubString > 0) {
                                if (findSimilarity(
                                        harmonizePotentialDomainName(item.decryptedName).toLowerCasedTrimmedString(),
                                        searchText
                                    ) > similarityThresholdOrSubString
                                ) item
                                else null
                            } else if (item.decryptedName.lowercase()
                                    .contains(searchText.lowercasedTrimmed)
                            ) item else null
                        }
                    )
                else null
                listOfNotNull(j1, j2)
            }
        }
    }

    fun launchCancelJobs(atomicBlock: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAllJobs(atomicBlock)
        }
    }
}