package fi.iki.ede.safe.gpm.ui.models

import android.util.Log
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

private const val TAG = "ImportMergeDataRepository"

class ImportMergeDataRepository {
    private val _unprocessedGPMs = mutableListOf<SavedGPM>()
    private val _displayedUnprocessedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    private val _savedSiteEntries = mutableListOf<DecryptableSiteEntry>()
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedUnprocessedGPMs: StateFlow<List<SavedGPM>> = _displayedUnprocessedGPMs
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries

    private val modificationRequests = MutableSharedFlow<ModificationRequest>()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun debug(message: String) {
        if (false) {
            Log.d(TAG,message)
        }
    }

    init {
        repositoryScope.launch {
            modificationRequests.collect { request ->
                when (request) {
                    is ModificationRequest.InitializeSiteEntryListAndDisplayListToGivenList -> {
                        debug("InitializeSiteEntryListAndDisplayListToGivenList ${request.siteEntries.size}")
                        _savedSiteEntries.clear()
                        _savedSiteEntries.addAll(request.siteEntries)
                        _displayedSiteEntries.value = _savedSiteEntries.toList()
                    }

                    is ModificationRequest.InitializeUnprocessedGPMAndDisplayListToGivenList -> {
                        debug("InitializeUnprocessedGPMAndDisplayListToGivenList ${request.savedGPMs.size}")
                        _unprocessedGPMs.clear()
                        _unprocessedGPMs.addAll(request.savedGPMs)
                        _displayedUnprocessedGPMs.value = _unprocessedGPMs.toList()
                    }

                    is ModificationRequest.ResetGPMDisplayListToAllUnprocessed ->
                        _displayedUnprocessedGPMs.value = _unprocessedGPMs.toList().also {
                            debug("ResetGPMDisplayListToAllUnprocessed ${it.size}")
                        }

                    is ModificationRequest.ResetSiteEntryDisplayListToAllSaved ->
                        _displayedSiteEntries.value = _savedSiteEntries.toList().also {
                            debug("ResetSiteEntryDisplayListToAllSaved ${it.size}")
                        }

                    is ModificationRequest.AddGpmToDisplayList ->
                        _displayedUnprocessedGPMs.update { it + request.savedGPM }.also {
                            debug("AddGpmToDisplayList ${request.savedGPM.cachedDecryptedName}")
                        }

                    is ModificationRequest.AddSiteEntryToDisplayList ->
                        _displayedSiteEntries.update { it + request.siteEntry }.also {
                            debug("AddSiteEntryToDisplayList ${request.siteEntry.cachedPlainDescription}")
                        }

                    is ModificationRequest.RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists -> {
                        if (!_unprocessedGPMs.removeAll { it.id == request.id }) {
                            Log.d(TAG, "Couldn't remove(all) GPM $request.id from _unprocessedGPMs")
                        }
                        _displayedUnprocessedGPMs.update {
                            it.filterNot { gpm -> gpm.id == request.id }
                        }.also {
                            debug("RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists ${request.id}")
                        }
                    }

                    is ModificationRequest.EmptyGpmDisplayLists -> {
                        debug("EmptyGpmDisplayLists")
                        _displayedUnprocessedGPMs.value = emptyList()
                    }

                    is ModificationRequest.EmptySiteEntryDisplayLists -> {
                        debug("EmptySiteEntryDisplayLists")
                        _displayedSiteEntries.value = emptyList()
                    }
                }
            }
        }
    }

    fun removeAllMatchingGpmsFromDisplayAndUnprocessedLists(id: Long) {
        repositoryScope.launch {
            modificationRequests.emit(
                ModificationRequest.RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists(
                    id
                )
            )
        }
    }

    suspend fun emptyGPMDisplayList() {
        modificationRequests.emit(ModificationRequest.EmptyGpmDisplayLists)
    }

    suspend fun emptySiteEntryDisplayList() {
        modificationRequests.emit(ModificationRequest.EmptySiteEntryDisplayLists)
    }

    fun addDisplayItem(items: List<Any>) {
        repositoryScope.launch {
            items.forEach { item ->
                when (item) {
                    is WrappedDecryptableSiteEntry ->
                        modificationRequests.emit(ModificationRequest.AddSiteEntryToDisplayList(item.siteEntry))

                    is DecryptableSiteEntry ->
                        modificationRequests.emit(ModificationRequest.AddSiteEntryToDisplayList(item))

                    is SavedGPM ->
                        modificationRequests.emit(ModificationRequest.AddGpmToDisplayList(item))
                }
            }
        }
    }

    internal suspend fun initializeUnprocessedGPMAndDisplayListToGivenList(newGPMs: Set<SavedGPM>) {
        modificationRequests.emit(
            ModificationRequest.InitializeUnprocessedGPMAndDisplayListToGivenList(
                newGPMs.toList()
            )
        )
    }

    suspend fun resetGPMDisplayListToAllUnprocessed() {
        modificationRequests.emit(ModificationRequest.ResetGPMDisplayListToAllUnprocessed)
    }

    internal suspend fun initializeSiteEntryListAndDisplayListToGivenList(newSiteEntries: List<DecryptableSiteEntry>) {
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
            DataType.WrappedDecryptableSiteEntry -> _savedSiteEntries.map {
                WrappedDecryptableSiteEntry(
                    it
                )
            }.toList()
            // Handle other data types similarly
        }
    }
}