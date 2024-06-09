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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    private val removeGPMs = mutableSetOf<SavedGPM>()

    fun removeGPM(id: Long) {
        println("Remove GPM $id")
        try {
            val gpm = _originalGPMs.first { it.id == id }
            _originalGPMs.removeAll { it.id == id }
            // will
            if (_isLoading.value == true) {
                removeGPMs.add(gpm)
            } else {
                _displayedGPMs.update { currentList ->
                    currentList.filter { it.id != id }
                }
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

    fun applyMatchingPasswords() {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.postValue(true)
            _displayedGPMs.value = emptyList()
            _displayedSiteEntries.value = emptyList()

            try {
                _originalSiteEntries.forEach { siteEntry ->
                    checkAndRemoveGPMs()
                    _originalGPMs.forEach { gpm ->
                        if (!requestSearchCancellation) {
                            if (gpm.decryptedPassword == siteEntry.plainPassword) {
                                _displayedGPMs.value += gpm
                                _displayedSiteEntries.value += siteEntry
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                println("applyMatchingPasswords failed $ex")
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

            try {
                _originalSiteEntries.forEach { siteEntry ->
                    checkAndRemoveGPMs()
                    _originalGPMs.forEach { gpm ->
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
            } catch (ex: Exception) {
                println("Failed to apply name match $ex")
            }
            println("Applied name match...")
            _isLoading.postValue(false)
            requestSearchCancellation = false
        }
    }

    private fun checkAndRemoveGPMs() {
        if (removeGPMs.size > 0) {
            try {
                val gpmsToRemove = removeGPMs
                removeGPMs.clear()
                _displayedGPMs.update { currentList ->
                    currentList.filterNot { savedGPM -> gpmsToRemove.contains(savedGPM) }
                }
            } catch (ex: Exception) {
                println("checkAndRemoveGPMs failed $ex")
            }
        }
    }

    fun clearMatchingNames() {
        _displayedGPMs.value = _originalGPMs
        _displayedSiteEntries.value = _originalSiteEntries
    }

    fun cancelOperation() {
        if (isLoading.value == true) {
            requestSearchCancellation = true
            _isLoading.postValue(false)
        }
    }

    fun search(
        similarityThresholdOrSubString: Double,
        searchText: LowerCaseTrimmedString,
        searchFromMyOwn: Boolean,
        searchFromBeingImported: Boolean
    ) {
        _isLoading.postValue(true)
        viewModelScope.launch(Dispatchers.Default) {
            val j1 =
                if (searchFromMyOwn) {
                    viewModelScope.launch(Dispatchers.Default) {
                        _displayedSiteEntries.value = emptyList()

                        try {
                            _originalSiteEntries.forEach { siteEntry ->
                                if (!requestSearchCancellation) {
                                    if (similarityThresholdOrSubString > 0) {
                                        val score = findSimilarity(
                                            harmonizePotentialDomainName(siteEntry.plainDescription).toLowerCasedTrimmedString(),
                                            searchText
                                        )
                                        if (score > similarityThresholdOrSubString) {
                                            _displayedSiteEntries.value += siteEntry
                                        }
                                    } else {
                                        if (siteEntry.plainDescription.lowercase()
                                                .contains(searchText.lowercasedTrimmed)
                                        ) {
                                            _displayedSiteEntries.value += siteEntry
                                        }
                                    }
                                } else {
                                    //println("Cancelled ${searchText.lowercasedTrimmed}")
                                }
                            }
                        } catch (ex: Exception) {
                            println("Search siteEntries failed $ex")
                        }
                        //println("Search siteEntries completed ${searchText.lowercasedTrimmed}")
                    }
                } else null

            val j2 = if (searchFromBeingImported) {
                viewModelScope.launch(Dispatchers.Default) {
                    _displayedGPMs.value = emptyList()
                    try {
                        _originalGPMs.forEach { gpm ->
                            if (!requestSearchCancellation) {
                                if (similarityThresholdOrSubString > 0) {
                                    val score = findSimilarity(
                                        harmonizePotentialDomainName(gpm.decryptedName).toLowerCasedTrimmedString(),
                                        searchText
                                    )
                                    if (score > similarityThresholdOrSubString) {
                                        _displayedGPMs.value += gpm
                                    }
                                } else {
                                    if (gpm.decryptedName.lowercase()
                                            .contains(searchText.lowercasedTrimmed)
                                    ) {
                                        _displayedGPMs.value += gpm
                                    }
                                }
                            } else {
                                //println("Cancelled ${searchText.lowercasedTrimmed}")
                            }
                        }
                    } catch (ex: Exception) {
                        println("Failed to search GPMs $ex")
                    }
                    //println("Search GPM completed ${searchText.lowercasedTrimmed}")
                }
            } else null

            j1?.join()
            j2?.join()
            //println("Searches completed")
            _isLoading.postValue(false)
            requestSearchCancellation = false
        }
    }
}