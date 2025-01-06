package fi.iki.ede.gpmui.models

import android.util.Log
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmui.DataModelIF
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

class ImportMergeDataRepository(datamodel: DataModelIF) {
    private val _displayedUnprocessedGPMs = MutableStateFlow<List<SavedGPM>>(emptyList())
    private val _displayedSiteEntries = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    private val _connectedDisplayItems =
        MutableStateFlow<List<Pair<DecryptableSiteEntry, SavedGPM>>>(emptyList())
    val displayedUnprocessedGPMs: StateFlow<List<SavedGPM>> = _displayedUnprocessedGPMs
    val displayedSiteEntries: StateFlow<List<DecryptableSiteEntry>> = _displayedSiteEntries
    val connectedDisplayItems: StateFlow<List<Pair<DecryptableSiteEntry, SavedGPM>>> =
        _connectedDisplayItems
    private val modificationRequests =
        MutableSharedFlow<ModificationRequest>()
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
                            GPMDataModel.unprocessedGPMsFlow.value.toList().also {
                                debug("ResetGPMDisplayListToAllUnprocessed ${it.size}")
                            }

                    is ModificationRequest.ResetSiteEntryDisplayListToAllSaved ->
                        _displayedSiteEntries.value =
                            datamodel.fetchSiteEntriesStateFlow().value.toList().also {
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

                    is ModificationRequest.AddConnectedDisplayItem ->
                        _connectedDisplayItems.update { it + request.connectedEntry }.also {
                            debug("AddConnectedDisplayItem ${request.connectedEntry}")
                        }

                    is ModificationRequest.RemoveConnectedDisplayItem ->
                        _connectedDisplayItems.update { it - request.connectedEntry }.also {
                            debug("RemoveConnectedDisplayItem ${request.connectedEntry}")
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
                        _connectedDisplayItems.value = emptyList()
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

    fun removeConnectedDisplayItem(pair: Pair<DecryptableSiteEntry, SavedGPM>) {
        repositoryScope.launch {
            modificationRequests.emit(
                ModificationRequest.RemoveConnectedDisplayItem(
                    pair
                )
            )
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

    suspend fun emptyGPMDisplayList() = CompletableDeferred<Unit>().apply {
        modificationRequests.emit(
            ModificationRequest.EmptyGpmDisplayLists(
                this
            )
        )
    }

    suspend fun emptySiteEntryDisplayList() = CompletableDeferred<Unit>().apply {
        modificationRequests.emit(
            ModificationRequest.EmptySiteEntryDisplayLists(
                this
            )
        )
    }

    fun addDisplayItem(item: Any) {
        repositoryScope.launch {
            when (item) {
                is WrappedDecryptableSiteEntry ->
                    modificationRequests.emit(
                        ModificationRequest.AddSiteEntryToDisplayList(
                            item.siteEntry
                        )
                    )

                is DecryptableSiteEntry ->
                    modificationRequests.emit(
                        ModificationRequest.AddSiteEntryToDisplayList(
                            item
                        )
                    )

                is SavedGPM ->
                    modificationRequests.emit(
                        ModificationRequest.AddGpmToDisplayList(
                            item
                        )
                    )
            }
        }
    }

    fun addConnectedDisplayItem(item: Pair<DecryptableSiteEntry, SavedGPM>) {
        repositoryScope.launch {
            modificationRequests.emit(
                ModificationRequest.AddConnectedDisplayItem(
                    item
                )
            )
        }
    }

    suspend fun resetGPMDisplayListToAllUnprocessed() {
        modificationRequests.emit(ModificationRequest.ResetGPMDisplayListToAllUnprocessed)
    }

    suspend fun resetSiteEntryDisplayListToAllSaved() {
        modificationRequests.emit(ModificationRequest.ResetSiteEntryDisplayListToAllSaved)
    }
}