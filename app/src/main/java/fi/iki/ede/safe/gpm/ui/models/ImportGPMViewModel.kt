package fi.iki.ede.safe.gpm.ui.models

import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class ImportGPMViewModel : ViewModel() {
    // LiveData to signal the UI to show loading
    private val _isWorkingAndProgress = MutableLiveData(false to null as Float?)
    val isWorkingAndProgress: LiveData<Pair<Boolean, Float?>> = _isWorkingAndProgress

    val dataRepository = DataRepository()

    private val jobManager = JobManager { completed, percentCompleted ->
        _isWorkingAndProgress.postValue(completed to percentCompleted)
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
            val importedGPMs = try {
                val imports = DataModel._savedGPMs.filter { savedGPM ->
                    !savedGPM.flaggedIgnored && DataModel._siteEntryToSavedGPM.none { (_, savedGPMList) -> savedGPM in savedGPMList }
                }.toSet()
                // IF user restored a database in the MEAN TIME, that means our previous export
                // is now totally unreadable (since our import / doesn't change gpm imports)
                val decryptTest = imports.firstOrNull()?.decryptedName
                imports
            } catch (ex: javax.crypto.BadPaddingException) {
                Log.e("ImportTest", "BadPaddingException: delete all GPM imports")
                // sorry dude, no point keeping imports we've NO WAY ever ever reading
                CoroutineScope(Dispatchers.IO).launch {
                    DataModel.deleteAllSavedGPMs()
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

    // TODO: PRE-MANGLE! calculate hash of all the PWDs and compare those
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
            // TODO: ScoreCOnfig!
            val threshold = 0.55
            jobManager.cancelAndAddNewJob {
                launchIterateLists("applyMatchingNames",
//                    dataRepository.giveOriginalListOf<DecryptableSiteEntry>(),
//                    dataRepository.giveOriginalListOf<SavedGPM>(),
                    dataRepository.getList(DataType.DecryptableSiteEntry) as List<DecryptableSiteEntry>,
                    dataRepository.getList(DataType.GPM) as List<SavedGPM>,
                    start = { },
                    compare = { outerEntry, innerEntry ->
                        val eka =
                            harmonizePotentialDomainName(outerEntry.plainDescription).toLowerCasedTrimmedString()
                        val toka =
                            harmonizePotentialDomainName(innerEntry.decryptedName).toLowerCasedTrimmedString()

                        val score = findSimilarity(
                            eka, toka
                        )
                        if (score > threshold)
                            println("$score = ${eka.lowercasedTrimmed} == ${toka.lowercasedTrimmed}")
                        if (score > threshold
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
        regex: Regex? = null,
    ) {
        if (searchText.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(
                similarityThresholdOrSubString,
                searchText.toLowerCasedTrimmedString(),
                searchFromMyOwn,
                searchFromBeingImported,
                regex
            )
        }
    }

    private fun performSearch(
        similarityThresholdOrSubString: Double,
        searchText: LowerCaseTrimmedString,
        searchFromMyOwn: Boolean,
        searchFromBeingImported: Boolean,
        regex: Regex? = null
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
                            } else if (regex == null && item.plainDescription.lowercase()
                                    .contains(searchText.lowercasedTrimmed)
                            ) item
                            else if (regex != null && regex.containsMatchIn(item.plainDescription.lowercase())
                            ) item
                            else null
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
                            println("Searching for ${searchText.lowercasedTrimmed} in ${item.decryptedName}")
                            if (similarityThresholdOrSubString > 0) {
                                if (findSimilarity(
                                        harmonizePotentialDomainName(item.decryptedName).toLowerCasedTrimmedString(),
                                        searchText
                                    ) > similarityThresholdOrSubString
                                ) item
                                else null
                            } else if (regex == null && item.decryptedName.lowercase()
                                    .contains(searchText.lowercasedTrimmed)
                            ) item
                            else if (regex != null && regex.containsMatchIn(item.decryptedName.lowercase())
                            ) item
                            else null
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