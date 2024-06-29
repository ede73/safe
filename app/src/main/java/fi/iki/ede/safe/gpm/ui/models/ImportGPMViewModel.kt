package fi.iki.ede.safe.gpm.ui.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.iki.ede.gpm.changeset.harmonizePotentialDomainName
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.similarity.LowerCaseTrimmedString
import fi.iki.ede.gpm.similarity.findSimilarity
import fi.iki.ede.gpm.similarity.toLowerCasedTrimmedString
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ImportGPMViewModel : ViewModel() {
    // LiveData to signal the UI to show loading
    private val _isWorkingAndProgress = MutableLiveData(false to null as Float?)
    val isWorkingAndProgress: LiveData<Pair<Boolean, Float?>> = _isWorkingAndProgress

    val importMergeDataRepository = ImportMergeDataRepository()

    private val jobManager = JobManager { completed, percentCompleted ->
        _isWorkingAndProgress.postValue(completed to percentCompleted)
    }

    private val collectFlowJob = viewModelScope.launch {
        DataModel.siteEntryToSavedGPMStateFlow.combine(DataModel.siteEntriesStateFlow)
        { link: LinkedHashMap<DecryptableSiteEntry, Set<SavedGPM>>, siteEntries: List<DecryptableSiteEntry> ->
            siteEntries.associateWith { siteEntry -> link.filter { it.key.id == siteEntry.id }.values.flatten() }
        }.combine(DataModel.savedGPMsFlow) { allSiteEntriesAndTheirLinkedGpms, allSavedGpms ->
            // Pair the combined map with the current list of SavedGPMs
            allSiteEntriesAndTheirLinkedGpms to allSavedGpms
        }.collect { (allSiteEntriesAndTheirLinkedGpms, allSavedGpms) ->
            val alreadyLinkedGpms = allSiteEntriesAndTheirLinkedGpms.values.flatten().toSet()
            val notIgnoredGPMs = allSavedGpms.filter { gpm -> !gpm.flaggedIgnored }.toSet()
            importMergeDataRepository.initializeUnprocessedGPMAndDisplayListToGivenList(
                notIgnoredGPMs - alreadyLinkedGpms
            )
            importMergeDataRepository.initializeSiteEntryListAndDisplayListToGivenList(
                allSiteEntriesAndTheirLinkedGpms.keys.toList()
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectFlowJob.cancel()
    }

    fun removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id: Long) {
        importMergeDataRepository.removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id)
    }

    // thread safe abstraction allowing implementing easy match algorithms
    // and the heavy lifting of editing lists while iterating them is done here
    // not messing up the call site
    // TODO: ADD PRE-MANGLE! instead of decrypting 300000 entries, do 400+700 , make hash and compare the hashes! (or parts)
    // TODO: pre-mangle, make all lowercase - for instance
    private fun <O, I> launchIterateLists(
        name: String,
        outerList: List<O>,
        innerList: List<I>,
        start: () -> Unit,
        compare: (outerItem: O, innerItem: I) -> Pair<O?, I?>,
        exceptionHandler: (Exception) -> Unit = {}
    ) = viewModelScope.launch(Dispatchers.Default) {

        start()

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
                            importMergeDataRepository.addDisplayItem(
                                listOfNotNull(
                                    addInnerItem,
                                    addOuterItem
                                )
                            )
                        }
                    }
//                    l.intersect(r).forEach {
//                        Log.d(TAG,"Matched $it")
//                    }
                }
            }
        } catch (ex: Exception) {
            firebaseRecordException("Something went wrong in $name", ex)
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

//    val l = mutableSetOf<String>()
//    val r = mutableSetOf<String>()

    // TODO: PRE-MANGLE! calculate hash of all the PWDs and compare those
    fun launchMatchingPasswordSearchAndResetDisplayLists() {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                importMergeDataRepository.emptyGPMDisplayList() // TODO: REMOVE, hoist reset control!
                importMergeDataRepository.emptySiteEntryDisplayList() // TODO: REMOVE, hoist reset control!
                launchIterateLists("applyMatchingPasswords",
                    importMergeDataRepository.getList(DataType.WrappedDecryptableSiteEntry) as List<WrappedDecryptableSiteEntry>,
                    importMergeDataRepository.getList(DataType.GPM) as List<SavedGPM>,
                    start = { },
                    compare = { outerEntry, innerEntry ->
//                        if (outerEntry.cachedDecryptedPassword.isNotBlank()) {
//                            l.add(outerEntry.cachedDecryptedPassword)
//                            r.add(innerEntry.cachedDecryptedPassword)
//                        }
                        if (outerEntry.cachedDecryptedPassword.isNotBlank() &&
                            outerEntry.cachedDecryptedPassword == innerEntry.cachedDecryptedPassword
                        ) {
                            outerEntry to innerEntry
                        } else null to null
                    })
            }
        }
    }

    fun launchMatchingNameSearchAndResetDisplayLists(similarityThreshold: Double) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                importMergeDataRepository.emptyGPMDisplayList() // TODO: REMOVE, hoist reset control!
                importMergeDataRepository.emptySiteEntryDisplayList() // TODO: REMOVE, hoist reset control!
                launchIterateLists("applyMatchingNames",
//                    dataRepository.giveOriginalListOf<DecryptableSiteEntry>(),
//                    dataRepository.giveOriginalListOf<SavedGPM>(),
                    importMergeDataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                    importMergeDataRepository.getList(DataType.GPM) as List<SavedGPM>,
                    start = { },
                    compare = { outerEntry, innerEntry ->
                        val eka =
                            harmonizePotentialDomainName(outerEntry.cachedPlainDescription).toLowerCasedTrimmedString()
                        val toka =
                            harmonizePotentialDomainName(innerEntry.cachedDecryptedName).toLowerCasedTrimmedString()

                        val simScore = findSimilarity(eka, toka)
                        if (simScore >= similarityThreshold) (outerEntry to innerEntry)
                        else null to null
                    }
                )
            }
        }
    }

    // Assuming 'scope' is your CoroutineScope, e.g., viewModelScope
    private var searchJob: Job? = null
    fun launchSearch(
        similarityThresholdOrSubString: Double,
        searchText: String,
        passwordSearchTarget: SearchTarget,
        gpmSearchTarget: SearchTarget,
        regex: Regex? = null,
    ) {
        if (searchText.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(750)
            performSearch(
                similarityThresholdOrSubString,
                searchText.toLowerCasedTrimmedString(),
                passwordSearchTarget,
                gpmSearchTarget,
                regex
            )
        }
    }

    private fun performSearch(
        similarityThresholdOrSubString: Double,
        searchText: LowerCaseTrimmedString,
        passwordSearchTarget: SearchTarget,
        gpmSearchTarget: SearchTarget,
        regex: Regex? = null
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJobs {
                val passwordSearchThread = if (passwordSearchTarget != SearchTarget.IGNORE)
                    launchIterateList(
                        "searchSiteEntries",
//                        dataRepository.giveOriginalListOf<DecryptableSiteEntry>(),
                        if (passwordSearchTarget == SearchTarget.SEARCH_FROM_DISPLAYED)
                            importMergeDataRepository.displayedSiteEntries.value.toList()
                        else
                            importMergeDataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                        start = { },
                        compare = { item ->
                            if (similarityThresholdOrSubString > 0) {
                                if (findSimilarity(
                                        harmonizePotentialDomainName(item.cachedPlainDescription).toLowerCasedTrimmedString(),
                                        searchText
                                    ) > similarityThresholdOrSubString
                                ) item else null
                            } else if (regex == null && item.cachedPlainDescription.contains(
                                    searchText.lowercasedTrimmed,
                                    ignoreCase = true
                                )
                            ) item
                            else if (regex != null && regex.containsMatchIn(item.cachedPlainDescription)
                            ) item
                            else null
                        }
                    ).also {
                        // if we're to SEARCH, need to reset the display
                        importMergeDataRepository.emptySiteEntryDisplayList()
                    }
                else null

                val gpmSearchThread = if (gpmSearchTarget != SearchTarget.IGNORE)
                    launchIterateList(
                        "searchGPMs",
//                        dataRepository.giveOriginalListOf<SavedGPM>(),
                        if (gpmSearchTarget == SearchTarget.SEARCH_FROM_DISPLAYED)
                            importMergeDataRepository.displayedUnprocessedGPMs.value.toList()
                        else
                            importMergeDataRepository.getList(DataType.GPM) as List<SavedGPM>,
                        start = { },
                        compare = { item ->
                            if (similarityThresholdOrSubString > 0) {
                                if (findSimilarity(
                                        harmonizePotentialDomainName(item.cachedDecryptedName).toLowerCasedTrimmedString(),
                                        searchText
                                    ) > similarityThresholdOrSubString
                                ) item
                                else null
                            } else if (regex == null && item.cachedDecryptedName
                                    .contains(searchText.lowercasedTrimmed, ignoreCase = true)
                            ) item
                            else if (regex != null && regex.containsMatchIn(item.cachedDecryptedName)
                            ) item
                            else null
                        }
                    ).also {
                        // if we're to SEARCH, need to reset the display
                        importMergeDataRepository.emptyGPMDisplayList()
                    }
                else null
                listOfNotNull(passwordSearchThread, gpmSearchThread)
            }
        }
    }

    fun cancelAllJobs(atomicBlock: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAllJobs(atomicBlock)
        }
    }
}