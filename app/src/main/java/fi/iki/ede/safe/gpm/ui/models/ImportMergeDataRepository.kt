package fi.iki.ede.safe.gpm.ui.models

import android.util.Log
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ImportMergeDataRepository"

class ImportMergeDataRepository {
    private val _displayedUnprocessedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val displayedUnprocessedGPMs: StateFlow<List<SavedGPM>> = _displayedUnprocessedGPMs
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries
    private val modificationRequests = MutableSharedFlow<ModificationRequest>()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val collector: Job

    fun debug(message: String) {
        if (false) {
            Log.d(TAG, message)
        }
    }

    init {
        collector = repositoryScope.launch {
            modificationRequests.collect { request ->
                when (request) {
                    is ModificationRequest.ResetGPMDisplayListToAllUnprocessed ->
                        _displayedUnprocessedGPMs.value =
                            DataModel.unprocessedGPMsFlow.value.toList().also {
                                debug("ResetGPMDisplayListToAllUnprocessed ${it.size}")
                            }

                    is ModificationRequest.ResetSiteEntryDisplayListToAllSaved ->
                        _displayedSiteEntries.value =
                            DataModel.siteEntriesStateFlow.value.toList().also {
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
                        _displayedUnprocessedGPMs.update {
                            it.filterNot { gpm -> gpm.id == request.id }
                        }.also {
                            debug("RemoveAllMatchingGpmsFromDisplayAndUnprocessedLists ${request.id}")
                        }
                    }

                    is ModificationRequest.EmptyGpmDisplayLists -> {
                        debug("EmptyGpmDisplayLists")
                        _displayedUnprocessedGPMs.value = emptyList()
                        request.completion.complete(Unit)
                    }

                    is ModificationRequest.EmptySiteEntryDisplayLists -> {
                        debug("EmptySiteEntryDisplayLists")
                        _displayedSiteEntries.value = emptyList()
                        request.completion.complete(Unit)
                    }
                }
            }
        }
    }

    fun onCleared() {
        collector.cancel()
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

    suspend fun emptyGPMDisplayList() = CompletableDeferred<Unit>().apply {
        modificationRequests.emit(ModificationRequest.EmptyGpmDisplayLists(this))
    }

    suspend fun emptySiteEntryDisplayList() = CompletableDeferred<Unit>().apply {
        modificationRequests.emit(ModificationRequest.EmptySiteEntryDisplayLists(this))
    }

    fun addDisplayItem(item: Any) {
        repositoryScope.launch {
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

    fun addConnectedDisplayItem(item: Pair<DecryptableSiteEntry, SavedGPM>) {
        repositoryScope.launch {
            // TODO: REMOVE - use the PAIR
            modificationRequests.emit(ModificationRequest.AddSiteEntryToDisplayList(item.first))
            modificationRequests.emit(ModificationRequest.AddGpmToDisplayList(item.second))
        }
    }

    suspend fun resetGPMDisplayListToAllUnprocessed() {
        modificationRequests.emit(ModificationRequest.ResetGPMDisplayListToAllUnprocessed)
    }

    suspend fun resetSiteEntryDisplayListToAllSaved() {
        modificationRequests.emit(ModificationRequest.ResetSiteEntryDisplayListToAllSaved)
    }
}