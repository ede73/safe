package fi.iki.ede.safe.gpm.ui.models

import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DecryptableSiteEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImportMergeDataRepository {
    private val _unprocessedGPMs = mutableListOf<SavedGPM>()
    private val _displayedUnprocessedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    private val _savedSiteEntries = mutableListOf<DecryptableSiteEntry>()
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedUnprocessedGPMs: StateFlow<List<SavedGPM>> = _displayedUnprocessedGPMs
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries

    private val modificationRequests = MutableSharedFlow<ModificationRequest>()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        repositoryScope.launch {
            modificationRequests.collect { request ->
                when (request) {
                    is ModificationRequest.InitializeSiteEntryListAndDisplayListToGivenList -> {
                        _savedSiteEntries.clear()
                        _savedSiteEntries.addAll(request.siteEntries)
                        _displayedSiteEntries.value = _savedSiteEntries.toList()
                        println("SiteEntry list initialixed to all saved site entries")
                    }

                    is ModificationRequest.InitializeUnprocessedGPMAndDisplayListToGivenList -> {
                        println("Reinitialize saved GPM list from given list")
                        _unprocessedGPMs.clear()
                        _unprocessedGPMs.addAll(request.savedGPMs)
                        _displayedUnprocessedGPMs.value = _unprocessedGPMs.toList()
                        println("GPM list initialize dto ALL UN PROCESSED=given list from rquest")
                    }

                    is ModificationRequest.ResetGPMDisplayListToAllUnprocessed ->
                        _displayedUnprocessedGPMs.value = _unprocessedGPMs.toList().also {
                            println("ResetED GPM Display list to all unprocessed")
                        }

                    is ModificationRequest.ResetSiteEntryDisplayListToAllSaved ->
                        _displayedSiteEntries.value = _savedSiteEntries.toList().also {
                            println("ResetED Site Etry list to ALL SAVED")
                        }

                    is ModificationRequest.DisplayGPM ->
                        _displayedUnprocessedGPMs.update { it + request.savedGPM }

                    is ModificationRequest.DisplaySiteEntry ->
                        _displayedSiteEntries.update { it + request.siteEntry }

                    is ModificationRequest.RemoveGPM -> {
                        if (!_unprocessedGPMs.removeAll { it.id == request.id }) {
                            println("GPM $request.id not found (wasnt removed from ORIGINAL)")
                        }
                        _displayedUnprocessedGPMs.update {
                            it.filterNot { gpm -> gpm.id == request.id }
                        }
                    }

                    is ModificationRequest.EmptyGPMDisplayLists -> {
                        _displayedUnprocessedGPMs.value = emptyList()
                        println("GPM set to EMPTY")
                    }

                    is ModificationRequest.EmptySiteEntryDisplayLists -> {
                        _displayedSiteEntries.value = emptyList()
                        println("SiteEntryList set to EMPTY")
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


    suspend fun emptyGPMDisplayList() {
        modificationRequests.emit(ModificationRequest.EmptyGPMDisplayLists)
    }

    suspend fun emptySiteEntryDisplayList() {
        modificationRequests.emit(ModificationRequest.EmptySiteEntryDisplayLists)
    }

    fun addDisplayItem(items: List<Any>) {
        items.forEach {
            println("Add to displsy $it")
        }
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

    suspend fun initializeUnprocessedGPMAndDisplayListToGivenList(newGPMs: Set<SavedGPM>) {
        modificationRequests.emit(
            ModificationRequest.InitializeUnprocessedGPMAndDisplayListToGivenList(
                newGPMs.toList()
            )
        )
    }

    suspend fun resetGPMDisplayListToAllUnprocessed() {
        modificationRequests.emit(ModificationRequest.ResetGPMDisplayListToAllUnprocessed)
    }

    suspend fun initializeSiteEntryListAndDisplayListToGivenList(newSiteEntries: List<DecryptableSiteEntry>) {
        modificationRequests.emit(
            ModificationRequest.InitializeSiteEntryListAndDisplayListToGivenList(
                newSiteEntries
            )
        )
    }

    suspend fun resetSiteEntryDisplayListToAllSaved() {
        modificationRequests.emit(ModificationRequest.ResetSiteEntryDisplayListToAllSaved)
    }

    fun getList(dataType: DataType): List<Any> {
        return when (dataType) {
            DataType.GPM -> _unprocessedGPMs.toList()
            DataType.DecryptableSiteEntry -> _savedSiteEntries.toList()
            // Handle other data types similarly
        }
    }
}