package fi.iki.ede.safe.ui.models

import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
                        if (_originalGPMs.removeAll { it.id == request.id }) {
                            println("GPM $request.id removed")
                        } else {
                            println("GPM $request.id not found (wasnt removed from ORIGINAL)")
                        }
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