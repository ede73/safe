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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ImportGPMViewModel : ViewModel() {
    // LiveData to signal the UI to show loading
    private val _isWorkingAndProgress = MutableLiveData(false to null as Float?)
    private val jobManager = JobManager { completed, percentCompleted ->
        _isWorkingAndProgress.postValue(completed to percentCompleted)
    }
    private var _displayedItemsAreConnected: Boolean = false
    val displayedItemsAreConnected: Boolean
        get() = _displayedItemsAreConnected
    val isWorkingAndProgress: LiveData<Pair<Boolean, Float?>> = _isWorkingAndProgress
    val importMergeDataRepository = ImportMergeDataRepository()

    init {
        viewModelScope.launch {
            importMergeDataRepository.resetGPMDisplayListToAllUnprocessed()
        }
        viewModelScope.launch {
            importMergeDataRepository.resetSiteEntryDisplayListToAllSaved()
        }
    }

    override fun onCleared() {
        super.onCleared()
        importMergeDataRepository.onCleared()
    }

    fun removeConnectedDisplayItem(siteEntry: DecryptableSiteEntry, linkedSavedGPM: SavedGPM) {
        importMergeDataRepository.removeConnectedDisplayItem(siteEntry to linkedSavedGPM)

    }

    fun removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id: Long) {
        importMergeDataRepository.removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id)
    }

    val singleListInnerSearch = listOf<Any?>(null)

    // thread safe abstraction allowing implementing easy match algorithms
    // and the heavy lifting of editing lists while iterating them is done here
    // not messing up the call site
    // TODO: ADD PRE-MANGLE! instead of decrypting 300000 entries, do 400+700 , make hash and compare the hashes! (or parts)
    // TODO: pre-mangle, make all lowercase - for instance
    private fun <O, I> launchIterateLists(
        name: String,
        outerList: List<O>,
        innerList: List<I>,
        start: () -> Unit = {},
        compare: (outerItem: O, innerItem: I) -> Pair<O, I?>?,
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
                            compare(outerEntry, innerEntry)?.let { (addOuterItem, addInnerItem) ->
                                if (innerList === singleListInnerSearch) {
                                    // this is a single list search
                                    importMergeDataRepository.addDisplayItem(addOuterItem as Any)
                                } else {
                                    if (addInnerItem != null) {
                                        importMergeDataRepository.addConnectedDisplayItem(
                                            Pair(
                                                if (addOuterItem is WrappedDecryptableSiteEntry)
                                                    addOuterItem.siteEntry
                                                else
                                                    addOuterItem as DecryptableSiteEntry,
                                                addInnerItem as SavedGPM
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
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
        start: () -> Unit = {},
        compare: (item: L) -> L?,
        exceptionHandler: (Exception) -> Unit = {}
    ) = launchIterateLists(
        name,
        list,
        singleListInnerSearch,
        start = { start() },
        compare = { l, _ -> compare(l)?.let { it to null } },
        exceptionHandler
    )

    // TODO: PRE-MANGLE! calculate hash of all the PWDs and compare those
    fun launchMatchingPasswordSearchAndResetDisplayLists() {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                _displayedItemsAreConnected = true
                importMergeDataRepository.emptySiteEntryDisplayList()
                    .await() // TODO: REMOVE, hoist reset control!
                importMergeDataRepository.emptyGPMDisplayList()
                    .await() // TODO: REMOVE, hoist reset control!
                launchIterateLists("applyMatchingPasswords",
                    DataModel.siteEntriesStateFlow.value.map { WrappedDecryptableSiteEntry(it) },
                    DataModel.unprocessedGPMsFlow.value.toList(),
                    compare = { outerEntry, innerEntry ->
                        if (outerEntry.cachedDecryptedPassword.isNotBlank() &&
                            outerEntry.cachedDecryptedPassword == innerEntry.cachedDecryptedPassword
                        ) outerEntry to innerEntry
                        else null
                    })
            }
        }
    }

    fun launchMatchingNameSearchAndResetDisplayLists(similarityThreshold: Double) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJob {
                _displayedItemsAreConnected = true
                importMergeDataRepository.emptySiteEntryDisplayList()
                    .await() // TODO: REMOVE, hoist reset control!
                importMergeDataRepository.emptyGPMDisplayList()
                    .await() // TODO: REMOVE, hoist reset control!
                launchIterateLists("applyMatchingNames",
                    DataModel.siteEntriesStateFlow.value,
                    DataModel.unprocessedGPMsFlow.value.toList(),
                    compare = { outerEntry, innerEntry ->
                        val siteEntryName =
                            harmonizePotentialDomainName(outerEntry.cachedPlainDescription).toLowerCasedTrimmedString()
                        val gpmName =
                            harmonizePotentialDomainName(innerEntry.cachedDecryptedName).toLowerCasedTrimmedString()

                        val simScore = findSimilarity(siteEntryName, gpmName)
                        if (simScore >= similarityThreshold) outerEntry to innerEntry
                        else null
                    }
                )
            }
        }
    }

    // Assuming 'scope' is your CoroutineScope, e.g., viewModelScope
    private var searchJob: Job? = null
    fun launchSearch(
        passwordSearchTarget: SearchTarget,
        gpmSearchTarget: SearchTarget,
        similarityThresholdOrSubString: Double,
        searchText: String,
        regex: Regex? = null,
    ) {
        if (searchText.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(750)
            performSearch(
                passwordSearchTarget,
                gpmSearchTarget,
                similarityThresholdOrSubString,
                searchText.toLowerCasedTrimmedString(),
                regex
            )
        }
    }

    private fun performSearch(
        passwordSearchTarget: SearchTarget,
        gpmSearchTarget: SearchTarget,
        similarityThresholdOrSubString: Double,
        searchText: LowerCaseTrimmedString,
        regex: Regex? = null
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAndAddNewJobs {
                _displayedItemsAreConnected = false
                val passwordSearchThread =
                    if (passwordSearchTarget != SearchTarget.IGNORE) singleListSearch(
                        "searchSiteEntries",
                        importMergeDataRepository::emptySiteEntryDisplayList,
                        if (passwordSearchTarget == SearchTarget.SEARCH_FROM_DISPLAYED)
                            importMergeDataRepository.displayedSiteEntries.value.toList()
                        else
                            DataModel.siteEntriesStateFlow.value,
                        similarityThresholdOrSubString,
                        regex,
                        searchText
                    ) { item -> item.cachedPlainDescription }
                    else null

                val gpmSearchThread = if (gpmSearchTarget != SearchTarget.IGNORE) singleListSearch(
                    "searchGPMs",
                    importMergeDataRepository::emptyGPMDisplayList,
                    if (gpmSearchTarget == SearchTarget.SEARCH_FROM_DISPLAYED)
                        importMergeDataRepository.displayedUnprocessedGPMs.value.toList()
                    else
                        DataModel.unprocessedGPMsFlow.value.toList(),
                    similarityThresholdOrSubString,
                    regex,
                    searchText
                ) { item -> item.cachedDecryptedName }
                else null
                listOfNotNull(passwordSearchThread, gpmSearchThread)
            }
        }
    }

    private suspend fun <L> singleListSearch(
        searchHintText: String,
        resetSearchList: suspend () -> CompletableDeferred<Unit>,
        listOfItemsToSearch: List<L>,
        similarityThresholdOrSubString: Double,
        regex: Regex?,
        needle: LowerCaseTrimmedString,
        haystack: (L) -> String
    ) = resetSearchList().await().let {
        // we sent reset display lest call out already, so by the time thread starts, list should be clear
        launchIterateList(
            searchHintText,
            listOfItemsToSearch,
            compare = { item ->
                if (similarityThresholdOrSubString > 0) {
                    if (findSimilarity(
                            harmonizePotentialDomainName(haystack(item)).toLowerCasedTrimmedString(),
                            needle
                        ) > similarityThresholdOrSubString
                    ) item else null
                } else if (regex == null && haystack(item).contains(
                        needle.lowercasedTrimmed,
                        ignoreCase = true
                    )
                ) item
                else if (regex != null && regex.containsMatchIn(haystack(item))) item
                else null
            }
        )
    }

    fun cancelAllJobs(atomicBlock: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Default).launch {
            jobManager.cancelAllJobs(atomicBlock)
        }
    }
}