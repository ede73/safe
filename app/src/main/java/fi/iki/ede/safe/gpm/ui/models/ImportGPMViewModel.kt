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
import kotlinx.coroutines.CoroutineName
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
        viewModelScope.launch(CoroutineName("resetGPMDisplayListToAllUnprocessed")) {
            importMergeDataRepository.resetGPMDisplayListToAllUnprocessed()
        }
        viewModelScope.launch(CoroutineName("resetSiteEntryDisplayListToAllSaved")) {
            importMergeDataRepository.resetSiteEntryDisplayListToAllSaved()
        }
    }

    override fun onCleared() {
        super.onCleared()
        importMergeDataRepository.onCleared()
    }

    // if search takes FOR EVER and user is already dragndropping, flow updates, but search
    // obviously runs on the COPY it was started with, to avoid concurrent mod AND adding
    // finalized items, we collect them here and ignore in the search
    private var excludedGPMs = setOf<Long>()
    fun removeConnectedDisplayItem(siteEntry: DecryptableSiteEntry, linkedSavedGPM: SavedGPM) {
        importMergeDataRepository.removeConnectedDisplayItem(siteEntry to linkedSavedGPM)
        excludedGPMs = excludedGPMs + linkedSavedGPM.id!!
    }

    fun removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id: Long) {
        importMergeDataRepository.removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id)
        excludedGPMs = excludedGPMs + id
    }

    // marker list for distributed single list search
    private val singleListInnerSearch = listOf<Any?>(null)

    // thread safe abstraction allowing implementing easy match algorithms
    // and the heavy lifting of editing lists while iterating them is done here
    // not messing up the call site
    private fun <O, I> launchIterateLists(
        name: String,
        outerList: List<O>,
        innerList: List<I>,
        start: () -> Unit = {},
        compare: (outerItem: O, innerItem: I) -> Pair<O, I?>?,
        exceptionHandler: (Exception) -> Unit = {}
    ) = viewModelScope.launch(Dispatchers.Default + CoroutineName("launchIterateLists:$name")) {
        excludedGPMs = emptySet()
        val maxParallelism = Runtime.getRuntime().availableProcessors()
        start()

        fun isGpmExcluded(gpm: I) = when (gpm) {
            is SavedGPM -> {
                excludedGPMs.contains(gpm.id)
            }

            else -> false
        }

        fun <I, O> foundMatchMaybeAdd(
            innerList: List<I>,
            addOuterItem: O,
            addInnerItem: I?
        ) {
            if (innerList === singleListInnerSearch) {
                // this is a single list search
                importMergeDataRepository.addDisplayItem(addOuterItem as Any)
            } else if (addInnerItem != null) {
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

        // allow iterating while editing
        val copyOfOuterList = outerList.toList()
        val copyOfInnerList = innerList.toList()
        val listTotalSearchSpace = copyOfOuterList.size * copyOfInnerList.size.toLong()
        val progress = Progress(listTotalSearchSpace) { percentCompleted ->
            jobManager.updateProgress(percentCompleted)
        }

        try {
            copyOfOuterList.chunked((copyOfOuterList.size + maxParallelism - 1) / maxParallelism)
                .forEach { chunkedOuterEntry ->
                    launch(CoroutineName("launchIterateLists:$name, chunk $chunkedOuterEntry.size")) {
                        chunkedOuterEntry.forEach outerExit@{ outerEntry ->
                            if (!isActive) return@outerExit
                            copyOfInnerList.forEach innerExit@{ innerEntry ->
                                if (!isActive || isGpmExcluded(innerEntry)) return@innerExit
                                progress.increment()
                                compare(
                                    outerEntry,
                                    innerEntry
                                )?.let { (addOuterItem, addInnerItem) ->
                                    foundMatchMaybeAdd(innerList, addOuterItem, addInnerItem)
                                }
                            }
                        }
                    }
                }
        } catch (ex: Exception) {
            firebaseRecordException("Something went wrong in $name", ex)
            exceptionHandler(ex)
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

    fun launchMatchingPasswordSearchAndResetDisplayLists() {
        CoroutineScope(Dispatchers.Default).launch(CoroutineName("launchMatchingPasswordSearchAndResetDisplayLists()")) {
            jobManager.cancelAndAddNewJob {

                _displayedItemsAreConnected = true

                importMergeDataRepository.emptySiteEntryDisplayList().await()
                importMergeDataRepository.emptyGPMDisplayList().await()

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
        CoroutineScope(Dispatchers.Default).launch(CoroutineName("launchMatchingNameSearchAndResetDisplayLists")) {
            jobManager.cancelAndAddNewJob {

                _displayedItemsAreConnected = true

                importMergeDataRepository.emptySiteEntryDisplayList().await()
                importMergeDataRepository.emptyGPMDisplayList().await()

                launchIterateLists("applyMatchingNames",
                    DataModel.siteEntriesStateFlow.value.toList(),
                    DataModel.unprocessedGPMsFlow.value.toList(),
                    compare = { outerEntry, innerEntry ->
                        val siteEntryName =
                            harmonizePotentialDomainName(outerEntry.cachedPlainDescription).toLowerCasedTrimmedString()
                        val gpmName =
                            harmonizePotentialDomainName(innerEntry.cachedDecryptedName).toLowerCasedTrimmedString()

                        if (similarityThreshold > 0) {
                            findSimilarity(siteEntryName, gpmName).let { simScore ->
                                (outerEntry to innerEntry).takeIf { simScore >= similarityThreshold }
                            }
                        } else {
                            (outerEntry to innerEntry).takeIf { siteEntryName == gpmName }
                        }
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
        searchJob = viewModelScope.launch(CoroutineName("launchSearch")) {
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
        CoroutineScope(Dispatchers.Default).launch(CoroutineName("performSearch")) {
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
        launchIterateList(
            searchHintText,
            listOfItemsToSearch,
            compare = { item ->
                if (similarityThresholdOrSubString > 0) {
                    item.takeIf {
                        findSimilarity(
                            harmonizePotentialDomainName(haystack(item)).toLowerCasedTrimmedString(),
                            needle
                        ) > similarityThresholdOrSubString
                    }
                } else if (regex == null && haystack(item).contains(needle.lowercasedTrimmed, true)
                ) item
                else if (regex != null && regex.containsMatchIn(haystack(item))) item
                else null
            }
        )
    }

    fun cancelAllJobs(atomicBlock: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Default).launch(CoroutineName("cancelAllJobs")) {
            jobManager.cancelAllJobs(atomicBlock)
        }
    }
}