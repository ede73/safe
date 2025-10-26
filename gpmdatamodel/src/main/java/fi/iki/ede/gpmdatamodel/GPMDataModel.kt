package fi.iki.ede.gpmdatamodel

import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseRecordException
import fi.iki.ede.logger.firebaseTry
import fi.iki.ede.preferences.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly


object GPMDataModel {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private const val TAG = "DataModelForGPM"

    init {
        if (BuildConfig.DEBUG) {
            setupDebugStateflowObserver()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupDebugStateflowObserver() {
        coroutineScope.launch {
            _allSavedGPMsFlow.collect { list ->
                Logger.d(
                    TAG,
                    "Debug observer: _savedGPMsFlow: (${
                        list.map { it.id }.joinToString(",")
                    })"
                )
            }
        }
        coroutineScope.launch {
            _siteEntryToSavedGPMFlow.collect { map ->
                Logger.d(
                    TAG,
                    "Debug observer: _siteEntryToSavedGPMFlow: (${
                        map.map { it.value }.flatten().joinToString(",")
                    })"
                )
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // GPM Imports: READ DATA CLASSES BTW. collection may change on new imports
    private val _allSavedGPMsFlow = MutableStateFlow<Set<SavedGPM>>(emptySet())

    val allSavedGPMsFlow: StateFlow<Set<SavedGPM>> = _allSavedGPMsFlow.asStateFlow()

    private val _siteEntryToSavedGPMFlow = MutableStateFlow(LinkedHashMap<DBID, Set<DBID>>())

    @get:TestOnly
    val siteEntryToSavedGPMStateFlow: StateFlow<LinkedHashMap<DBID, Set<DBID>>>
        get() = _siteEntryToSavedGPMFlow

    // combines all saved GPMs excluding ignored ones as well as those linked
    val unprocessedGPMsFlow: StateFlow<Set<SavedGPM>> = combine(
        _allSavedGPMsFlow,
        _siteEntryToSavedGPMFlow
    ) { setOfSavedGPMs, siteEntryToGpmsLink ->
        val linkedGPMs = siteEntryToGpmsLink.values.flatten().toSet()
        setOfSavedGPMs.filterNot { savedGPM ->
            savedGPM.flaggedIgnored || savedGPM.id in linkedGPMs
        }.toSet()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    fun storeNewGpmsAndReload(
        delete: Set<SavedGPM>,
        update: Map<IncomingGPM, SavedGPM>,
        add: Set<IncomingGPM>
    ) {
        GPMDB.deleteObsoleteSavedGPMs(delete)
        GPMDB.updateSavedGPMByIncomingGPM(update)
        GPMDB.addNewIncomingGPM(add)

        syncLoadAllSavedGPMs()
        syncLoadLinkedGPMs()
        Preferences.setLastModified()
    }

    fun syncLoadAllSavedGPMs() {
        GPMDB.fetchAllSavedGPMsFromDB(gpmsFlow = _allSavedGPMsFlow)
    }

    fun syncLoadLinkedGPMs() {
        val siteEntryIdGpmIdMappings = GPMDB.fetchAllSiteEntryGPMMappings()
        _siteEntryToSavedGPMFlow.value = LinkedHashMap(siteEntryIdGpmIdMappings)
    }

    fun markSavedGPMIgnored(savedGpmId: DBID) {
        Preferences.setLastModified()
        GPMDB.markSavedGPMIgnored(savedGpmId)
        _allSavedGPMsFlow.update { currentList ->
            currentList.map { savedGPM ->
                if (savedGPM.id == savedGpmId) {
                    savedGPM.copy(flaggedIgnored = true)
                } else {
                    savedGPM
                }
            }.toSet()
        }
    }

    fun getSavedGPM(savedGPMId: DBID): SavedGPM =
        _allSavedGPMsFlow.value.first { it.id == savedGPMId }

    fun getLinkedGPMs(siteEntryID: DBID): Set<SavedGPM> = firebaseTry {
        _siteEntryToSavedGPMFlow.value.filterKeys { it == siteEntryID }.values.flatten().map {
            getSavedGPM(it)
        }.toSet()
    }.firebaseCatch {
        emptySet()
    }

    // TODO: used only while developing? or fixing broken imports?
    fun deleteAllSavedGPMs() {
        GPMDB.deleteAllSavedGPMs()
        _allSavedGPMsFlow.value = emptySet()
    }

    fun loadFromDatabase() {
        _allSavedGPMsFlow.value = emptySet()
        _siteEntryToSavedGPMFlow.value = LinkedHashMap()

        // AND...actually LOAD THEM
        syncLoadAllSavedGPMs()
        syncLoadLinkedGPMs()
    }

    fun linkSaveGPMAndSiteEntry(siteEntry: DecryptableSiteEntry, savedGpmId: Long) {
        val map = _siteEntryToSavedGPMFlow.value.toMutableMap()
        map.keys.find { it == siteEntry.id }.let { existingEntry ->
            if (existingEntry != null) {
                val updatedSet = map[existingEntry]?.toMutableSet() ?: mutableSetOf()
                updatedSet.add(savedGpmId)
                map[existingEntry] = updatedSet
            } else {
                map[siteEntry.id!!] = setOf(savedGpmId)
            }
        }
        _siteEntryToSavedGPMFlow.value = LinkedHashMap(map)

        try {
            GPMDB.linkSaveGPMAndSiteEntry(siteEntry.id!!, savedGpmId)
            Preferences.setLastModified()
        } catch (ex: Exception) {
            firebaseRecordException(
                "Linking SE=${siteEntry.id} and GPM=${savedGpmId} failed (exists already?) should never happend",
                ex
            )
        }
    }
}