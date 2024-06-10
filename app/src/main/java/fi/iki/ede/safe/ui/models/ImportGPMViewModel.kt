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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DataRepository {
    private val _originalGPMs = mutableListOf<SavedGPM>()
    private val _displayedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    private val _originalSiteEntries = mutableListOf<DecryptableSiteEntry>()
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedGPMs: StateFlow<List<SavedGPM>> = _displayedGPMs
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries

    private val modificationRequests = MutableSharedFlow<ModificationRequest>()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        repositoryScope.launch {
            modificationRequests.collect { request ->
                when (request) {
                    is ModificationRequest.InitializeSiteEntries -> {
                        _originalSiteEntries.clear()
                        _originalSiteEntries.addAll(request.siteEntries)
                        _displayedSiteEntries.value = _originalSiteEntries
                    }

                    is ModificationRequest.InitializeGPMs -> {
                        _originalGPMs.clear()
                        _originalGPMs.addAll(request.savedGPMs)
                        _displayedGPMs.value = _originalGPMs
                    }

                    is ModificationRequest.ResetGPMDisplayList ->
                        _displayedGPMs.value = _originalGPMs

                    is ModificationRequest.ResetSiteEntryDisplayList ->
                        _displayedSiteEntries.value = _originalSiteEntries

                    is ModificationRequest.DisplayGPM ->
                        _displayedGPMs.update { it + request.savedGPM }

                    is ModificationRequest.DisplaySiteEntry ->
                        _displayedSiteEntries.update { it + request.siteEntry }

                    is ModificationRequest.RemoveGPM -> {
                        _originalGPMs.removeAll { it.id == request.id }
                        _displayedGPMs.update { it.filterNot { gpm -> gpm.id == request.id } }
                    }

                    is ModificationRequest.EmptyDisplayLists -> {
                        _displayedGPMs.value = emptyList()
                        _displayedSiteEntries.value = emptyList()
                    }
                }
            }
        }
    }

    fun removeGPM(id: Long) {
        repositoryScope.launch {
            modificationRequests.emit(ModificationRequest.RemoveGPM(id))
        }
    }


    fun emptyDisplayLists() {
        repositoryScope.launch {
            modificationRequests.emit(ModificationRequest.EmptyDisplayLists)
        }
    }

    fun addDisplayItem(items: List<Any>) {
        repositoryScope.launch {
            items.forEach { item ->
                when (item) {
                    is DecryptableSiteEntry ->
                        modificationRequests.emit(ModificationRequest.DisplaySiteEntry(item))

                    is SavedGPM ->
                        modificationRequests.emit(ModificationRequest.DisplayGPM(item))
                }
            }
        }
    }

    fun resetGPMDisplayList(newGPMs: Set<SavedGPM>? = null) {
        repositoryScope.launch {
            if (newGPMs != null)
                modificationRequests.emit(ModificationRequest.InitializeGPMs(newGPMs.toList()))
            else
                modificationRequests.emit(ModificationRequest.ResetGPMDisplayList)
        }
    }

    fun resetSiteEntryDisplayList(newSiteEntries: List<DecryptableSiteEntry>? = null) {
        repositoryScope.launch {
            if (newSiteEntries != null)
                modificationRequests.emit(ModificationRequest.InitializeSiteEntries(newSiteEntries))
            else
                modificationRequests.emit(ModificationRequest.ResetSiteEntryDisplayList)
        }
    }

//    inline fun <reified T> giveOriginalListOf(): MutableList<T> {
//        return when (T::class) {
//            // TODO: xxx
//            SavedGPM::class -> _originalGPMs.map { it.copy() } as MutableList<T>
//            DecryptableSiteEntry::class -> _originalSiteEntries as MutableList<T>
//            else -> throw Exception("Not supported")
//        }
//    }

    fun getList(dataType: DataType): List<Any> {
        return when (dataType) {
            DataType.GPM -> _originalGPMs.toList()
            DataType.DecryptableSiteEntry -> _originalSiteEntries.toList()
            // Handle other data types similarly
        }
    }
}

sealed class DataType {
    object GPM : DataType()
    object DecryptableSiteEntry : DataType()
    // Add other data types as needed
}

sealed class ModificationRequest {
    data class DisplayGPM(val savedGPM: SavedGPM) : ModificationRequest()
    data class DisplaySiteEntry(val siteEntry: DecryptableSiteEntry) : ModificationRequest()
    data class RemoveGPM(val id: Long) : ModificationRequest()
    data object ResetSiteEntryDisplayList : ModificationRequest()
    data object ResetGPMDisplayList : ModificationRequest()
    data object EmptyDisplayLists : ModificationRequest()
    data class InitializeSiteEntries(val siteEntries: List<DecryptableSiteEntry>) :
        ModificationRequest()

    data class InitializeGPMs(val savedGPMs: List<SavedGPM>) : ModificationRequest()
}

class ImportGPMViewModel(application: Application) : AndroidViewModel(application) {
    // LiveData to signal the UI to show loading
    private val _isWorking = MutableLiveData(false to null as Float?)
    val isWorking: LiveData<Pair<Boolean, Float?>> = _isWorking

    val dataRepository = DataRepository()

    private val jobManager = JobManager { completed, percentCompleted ->
        _isWorking.postValue(completed to percentCompleted)
    }

    init {
        loadGPMs()
        loadSiteEntries()
    }

    fun removeGPM(id: Long) {
        dataRepository.removeGPM(id)
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
            dataRepository.resetGPMDisplayList(importedGPMs)
        }
    }

    private fun loadSiteEntries() {
        dataRepository.resetSiteEntryDisplayList(DataModel.getPasswords())
    }

    // thread safe abstraction allowing implementing easy match algorithms
    // and the heavy lifting of editing lists while iterating them is done here
    // not messing up the call site
    private fun <O, I> launchIterateLists(
        name: String,
        outerList: List<O>,
        innerList: List<I>,
        start: () -> Unit,
        compare: (outerItem: O, innerItem: I) -> Pair<O?, I?>,
        exceptionHandler: (Exception) -> Unit = {}
    ) = viewModelScope.launch(Dispatchers.Default) {

        start()
        dataRepository.emptyDisplayLists()

        // allow iterating while editing
        val copyOfOuterList = outerList.toList()

        val progress =
            Progress(copyOfOuterList.size * innerList.size.toLong()) { percentCompleted ->
                jobManager.updateProgress(percentCompleted)
            }
        try {
            run jobCancelled@{
                if (isActive) {
                    copyOfOuterList.forEach { outerEntry ->
                        // TODO: this might NOT be enuf if GPMs removed(ignored/linked)
                        // while search on going, another solution is to add a removed items list
                        // and just in case subtract from here..
                        // just in case GPM list was modified, lets search on new entries
                        val copyOfInnerList = innerList.toList()
                        copyOfInnerList.forEach { innerEntry ->
                            if (!isActive) return@jobCancelled
                            progress.increment()
                            val (addOuterItem, addInnerItem) = compare(outerEntry, innerEntry)
                            dataRepository.addDisplayItem(listOfNotNull(addInnerItem, addOuterItem))
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
        start: () -> Unit,
        compare: (item: L) -> L?,
        exceptionHandler: (Exception) -> Unit = {}
    ) = launchIterateLists(
        name,
        list,
        listOf<L?>(null),
        start = { start() },
        compare = { l, _ -> compare(l) to null },
        exceptionHandler
    )

    fun launchSearchMatchingPasswords() {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                launchIterateLists("applyMatchingPasswords",
                    dataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                    dataRepository.getList(DataType.GPM) as List<SavedGPM>,
                    start = { },
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
        dataRepository.resetSiteEntryDisplayList()
        dataRepository.resetGPMDisplayList()
    }

    fun launchSearchMatchingNames() {
        CoroutineScope(Dispatchers.Default).launch {
            val threshold = 0.8
            jobManager.cancelAndAddNewJob {
                launchIterateLists("applyMatchingNames",
//                    dataRepository.giveOriginalListOf<DecryptableSiteEntry>(),
//                    dataRepository.giveOriginalListOf<SavedGPM>(),
                    dataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                    dataRepository.getList(DataType.GPM) as List<SavedGPM>,
                    start = { },
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

    fun clearMatchingNames() = clearMatchingPasswords()

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
//                        dataRepository.giveOriginalListOf<DecryptableSiteEntry>(),
                        dataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                        start = { },
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
//                        dataRepository.giveOriginalListOf<SavedGPM>(),
                        dataRepository.getList(DataType.GPM) as List<SavedGPM>,
                        start = { },
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