package fi.iki.ede.safe.model

import android.util.Log
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.time.ZonedDateTime

object DataModel {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        if (BuildConfig.DEBUG) {
            GlobalScope.launch {
                val TAG = "DataModel(observer)"
                _siteEntriesStateFlow.collect { list ->
                    Log.d(
                        TAG,
                        "Debug observer: _siteEntriesStateFlow:  (${
                            list.map { it.id }.joinToString(",")
                        })"
                    )
                }
            }
            GlobalScope.launch {
                _categoriesStateFlow.collect { list ->
                    Log.d(
                        TAG,
                        "Debug observer: _categoriesStateFlow:  (${
                            list.map { it.id }.joinToString(",")
                        })"
                    )
                }
            }
            GlobalScope.launch {
                _allSavedGPMsFlow.collect { list ->
                    Log.d(
                        TAG,
                        "Debug observer: _savedGPMsFlow: (${
                            list.map { it.id }.joinToString(",")
                        })"
                    )
                }
            }
            GlobalScope.launch {
                _siteEntryToSavedGPMFlow.collect { map ->
                    Log.d(
                        TAG,
                        "Debug observer: _siteEntryToSavedGPMFlow: (${
                            map.map { it.value }.flatten().joinToString(",")
                        })"
                    )
                }
            }
        }
    }

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

    // Categories state and events
    private val _categoriesStateFlow = MutableStateFlow<List<DecryptableCategoryEntry>>(emptyList())
    val categoriesStateFlow: StateFlow<List<DecryptableCategoryEntry>> get() = _categoriesStateFlow

    // SiteEntries state and events
    private val _siteEntriesStateFlow = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val siteEntriesStateFlow: StateFlow<List<DecryptableSiteEntry>> get() = _siteEntriesStateFlow

    private val _softDeletedStateFlow = MutableStateFlow<Set<DecryptableSiteEntry>>(emptySet())
    val softDeletedStateFlow: StateFlow<Set<DecryptableSiteEntry>> get() = _softDeletedStateFlow

    fun DecryptableSiteEntry.getCategory(): DecryptableCategoryEntry =
        _categoriesStateFlow.value.first { it.id == categoryId }

    fun DecryptableCategoryEntry.getCategory(): DecryptableCategoryEntry =
        _categoriesStateFlow.value.first { it.id == id }

    // (almost) no cost using (gets (encrypted) categories' site entries from memory)
    fun getSiteEntriesOfCategory(categoryId: DBID): List<DecryptableSiteEntry> =
        _siteEntriesStateFlow.value.filter { it.categoryId == categoryId }

    private fun getSavedGPM(savedGPMId: DBID): SavedGPM =
        _allSavedGPMsFlow.value.first { it.id == savedGPMId }

    // TODO: used only while developing? or fixing broken imports?
    fun deleteAllSavedGPMs() {
        DBHelperFactory.getDBHelper().deleteAllSavedGPMs()
        _allSavedGPMsFlow.value = emptySet()
    }

    // TODO: RENAME
    suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit = {}
    ) {
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            if (category.id == null) {
                // NEW
                category.id = db.addCategory(category)
                _categoriesStateFlow.value += category
                onAdd(category)
            } else {
                db.updateCategory(category.id!!, category)

                _categoriesStateFlow.updateListItemById(
                    category,
                    keySelector = { it.id!! }) { new, old ->
                    new.apply {
                        containedSiteEntryCount = old.containedSiteEntryCount
                    }
                }
            }
        }
    }

    fun deleteCategory(category: DecryptableCategoryEntry) {
        require(category.getCategory() == category) {
            "Alas category OBJECTS THEMSELVES are different, and SEARCH is needed"
        }
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            DBHelperFactory.getDBHelper().deleteCategory(category.id!!)
            _categoriesStateFlow.update { oldMap -> oldMap.filterNot { it.id == category.id } }
        }
    }

    fun getSiteEntry(siteEntryId: DBID): DecryptableSiteEntry =
        _siteEntriesStateFlow.value.first { it.id == siteEntryId }

    suspend fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit = {}
    ) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            if (siteEntry.id == null) {
                // we're adding a new PWD
                siteEntry.id = db.addSiteEntry(siteEntry)
                // Update model
                _siteEntriesStateFlow.value += siteEntry
                onAdd(siteEntry)
            } else {
                db.updateSiteEntry(siteEntry)
                _siteEntriesStateFlow.updateListItemById(siteEntry, keySelector = { it.id!! })
            }
            storeAllExtensionsToPreferences()
        }
    }

    fun moveSiteEntry(
        siteEntry: DecryptableSiteEntry,
        targetCategory: DecryptableCategoryEntry
    ) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            DBHelperFactory.getDBHelper()
                .updateSiteEntryCategory(siteEntry.id!!, targetCategory.id!!)

            val oldCategoryId = siteEntry.categoryId!!
            siteEntry.categoryId = targetCategory.id
            _siteEntriesStateFlow.updateListItemById(siteEntry, keySelector = { it.id!! })

            _categoriesStateFlow.updateListItemById(
                targetCategory,
                keySelector = { it.id!! })
            { new, old ->
                new.containedSiteEntryCount = old.containedSiteEntryCount + 1
                new
            }
            _categoriesStateFlow.updateListItemById(
                oldCategoryId,
                keySelector = { it.id!! }) { old ->
                old.apply {
                    containedSiteEntryCount = containedSiteEntryCount - 1
                }
            }
        }
    }

    fun emptyAllSoftDeleted(ids: Set<DBID>) {
        fi.iki.ede.preferences.Preferences.setLastModified()
        val db = DBHelperFactory.getDBHelper()
        ids.forEach { id ->
            db.hardDeleteSiteEntry(id)
            _softDeletedStateFlow.value -= _softDeletedStateFlow.value.filter { entry -> id == entry.id!! }
        }
    }

    fun restoreSiteEntry(siteEntry: DecryptableSiteEntry) {
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            _softDeletedStateFlow.value -= _softDeletedStateFlow.value.filter { it.id == siteEntry.id }
            // Find category this password belonged to, or first category if it doesn't exist
            val category = _categoriesStateFlow.value.firstOrNull { it.id == siteEntry.categoryId }
                ?: _categoriesStateFlow.value.firstOrNull()
            // or at worst, we have no categories..in which case restoration is impossible
            if (category != null) {
                val db = DBHelperFactory.getDBHelper()
                siteEntry.categoryId = category.id
                siteEntry.deleted = 0
                db.restoreSoftDeletedSiteEntry(siteEntry.id!!)
                _siteEntriesStateFlow.updateListItemById(
                    siteEntry,
                    keySelector = { it.id!! })
                _categoriesStateFlow.updateListItemById(
                    siteEntry.categoryId!!,
                    keySelector = { it.id!! }) { old ->
                    old.apply {
                        containedSiteEntryCount = old.containedSiteEntryCount + 1
                    }
                }
            }
        }
    }

    fun deleteSiteEntry(siteEntry: DecryptableSiteEntry) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        fi.iki.ede.preferences.Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            fi.iki.ede.preferences.Preferences.getSoftDeleteDays().let {
                if (it > 0) {
                    siteEntry.deleted = System.currentTimeMillis()
                    db.markSiteEntryDeleted(siteEntry.id!!)
                    _softDeletedStateFlow.value = _softDeletedStateFlow.value + siteEntry
                } else {
                    db.hardDeleteSiteEntry(siteEntry.id!!)
                }
            }

            _siteEntriesStateFlow.update {
                it.filterNot { entry -> entry.id == siteEntry.id }
            }
            _categoriesStateFlow.updateListItemById(
                siteEntry.categoryId!!,
                keySelector = { it.id!! }) { old ->
                old.apply {
                    containedSiteEntryCount = old.containedSiteEntryCount - 1
                }
            }
        }
    }

    private fun syncLoadAllSavedGPMs() {
        DBHelperFactory.getDBHelper().fetchAllSavedGPMsFromDB(gpmsFlow = _allSavedGPMsFlow)
    }

    private fun syncLoadLinkedGPMs() {
        val siteEntryIdGpmIdMappings = DBHelperFactory.getDBHelper().fetchAllSiteEntryGPMMappings()
        _siteEntryToSavedGPMFlow.value = LinkedHashMap(siteEntryIdGpmIdMappings)
    }

    fun markSavedGPMIgnored(savedGpmId: Long) {
        fi.iki.ede.preferences.Preferences.setLastModified()
        DBHelperFactory.getDBHelper().markSavedGPMIgnored(savedGpmId)
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

    // contraption just for unit tests
    var softDeletedMaxAgeProvider: () -> Int =
        { fi.iki.ede.preferences.Preferences.getSoftDeleteDays() }

    private fun storeAllExtensionsToPreferences() {
        fi.iki.ede.preferences.Preferences.storeAllExtensions(getAllSiteEntryExtensions().keys)
    }

    suspend fun loadFromDatabase() {
        _categoriesStateFlow.value = emptyList()
        _siteEntriesStateFlow.value = emptyList()
        _allSavedGPMsFlow.value = emptySet()
        _siteEntryToSavedGPMFlow.value = LinkedHashMap()
        _softDeletedStateFlow.value = emptySet()

        fun syncLoadSoftDeletedSiteEntries() {
            val db = DBHelperFactory.getDBHelper()
            val softDeletedSiteEntries = db.fetchAllRows(null, true).toSet()
            // are we past deletion time here?
            val softDeletedMaxAge = softDeletedMaxAgeProvider()
            val expiredSoftDeletedSiteEntries = softDeletedSiteEntries.filter {
                val softDeletedAge = fi.iki.ede.dateutils.DateUtils.getPeriodBetweenDates(
                    ZonedDateTime.now(),
                    fi.iki.ede.dateutils.DateUtils.unixEpochSecondsToLocalZonedDateTime(it.deleted),
                )
                if (softDeletedAge.days > softDeletedMaxAge) {
                    fi.iki.ede.preferences.Preferences.setLastModified()
                    db.hardDeleteSiteEntry(it.id!!)
                    true
                } else false
            }.toSet()
            _softDeletedStateFlow.value = softDeletedSiteEntries - expiredSoftDeletedSiteEntries
        }

        coroutineScope {
            launch(Dispatchers.IO) {
                val cats = async {
                    DBHelperFactory.getDBHelper().fetchAllCategoryRows(_categoriesStateFlow)
                }
                val sites = async {
                    DBHelperFactory.getDBHelper()
                        .fetchAllRows(siteEntriesFlow = _siteEntriesStateFlow)
                    // now that we know passwords, quickly update the category counts too...
                    _siteEntriesStateFlow.value.groupBy { it.categoryId }
                        .forEach { (categoryId, siteEntries)
                            ->
                            _categoriesStateFlow.updateListItemById(categoryId!!,
                                keySelector = { it.id!! }) {
                                it.apply {
                                    containedSiteEntryCount = siteEntries.size
                                }.copy()
                            }
                        }
                    storeAllExtensionsToPreferences()
                }
                val gpms = async {
                    syncLoadAllSavedGPMs()
                }
                launch { syncLoadSoftDeletedSiteEntries() }
                awaitAll(cats, sites)

                launch {
                    if (BuildConfig.DEBUG) {
                        dumpStrayCategoriesSiteEntries()
                    }
                }

                launch {
                    gpms.await()
                    // we can load these in the background, not critical
                    syncLoadLinkedGPMs()
                }
            }
        }
    }

    private suspend fun dumpStrayCategoriesSiteEntries() {
        coroutineScope {
            launch {
                // now kinda interesting integrity verification, do we have stray site entries?
                // ie. belonging to categories nonexistent
                fun filterAList(
                    aList: List<DecryptableSiteEntry>,
                    bList: List<DecryptableCategoryEntry>
                ): List<DecryptableSiteEntry> {
                    val bIds = bList.map { it.id }.toSet()
                    return aList.filter { it.categoryId !in bIds }
                }

                val straySiteEntries =
                    filterAList(_siteEntriesStateFlow.value, _categoriesStateFlow.value)

                straySiteEntries.forEach {
                    Log.e(
                        "DataModel",
                        "Stray SiteEntry id=${it.id}, category=${it.categoryId}, description=${it.cachedPlainDescription}"
                    )
                }
            }
        }
    }

    suspend fun dump() {
        if (BuildConfig.DEBUG) {
            coroutineScope {
                launch {
                    for (category in _categoriesStateFlow.value) {
                        Log.d(
                            TAG,
                            "Category id=${category.id} plainname=${category.plainName}"
                        ) // OK: Dump
                        for (siteEntry in getSiteEntriesOfCategory(category.id!!)) {
                            Log.d(
                                TAG,
                                "  SiteEntry id=${siteEntry.id}, description=${siteEntry.cachedPlainDescription},changed=${siteEntry.passwordChangedDate}"
                            ) // OK: Dump
                        }
                    }
                }
            }
        }
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
            DBHelperFactory.getDBHelper().linkSaveGPMAndSiteEntry(siteEntry.id!!, savedGpmId)
            fi.iki.ede.preferences.Preferences.setLastModified()
        } catch (ex: Exception) {
            firebaseRecordException(
                "Linking SE=${siteEntry.id} and GPM=${savedGpmId} failed (exists already?) should never happend",
                ex
            )
        }
    }

    suspend fun addGpmAsSiteEntry(
        savedGpmId: DBID,
        categoryId: DBID,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) {
        getSavedGPM(savedGpmId).let { gpm ->
            fi.iki.ede.preferences.Preferences.setLastModified()
            addOrUpdateSiteEntry(DecryptableSiteEntry(categoryId).apply {
                username = gpm.encryptedUsername
                password = gpm.encryptedPassword
                website = gpm.encryptedUrl
                description = gpm.encryptedName
                note = gpm.encryptedNote
            }, onAdd)
        }
    }

    fun getLinkedGPMs(siteEntryID: DBID): Set<SavedGPM> =
        _siteEntryToSavedGPMFlow.value.filterKeys { it == siteEntryID }.values.flatten().map {
            getSavedGPM(it)
        }.toSet()

    fun getAllSiteEntryExtensions(ignoreId: DBID? = null): Map<String, Set<String>> =
        _siteEntriesStateFlow.value.filter { it.id != ignoreId }
            .flatMap { it.plainExtensions.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten().filterNot(String::isBlank).toSet() }

    fun finishGPMImport(
        delete: Set<SavedGPM>,
        update: Map<IncomingGPM, SavedGPM>,
        add: Set<IncomingGPM>
    ) {
        DBHelperFactory.getDBHelper().deleteObsoleteSavedGPMs(delete)
        DBHelperFactory.getDBHelper().updateSavedGPMByIncomingGPM(update)
        DBHelperFactory.getDBHelper().addNewIncomingGPM(add)
        syncLoadAllSavedGPMs()
        syncLoadLinkedGPMs()
        fi.iki.ede.preferences.Preferences.setLastModified()
    }

    private const val TAG = "DataModel"
}

fun <K, V> MutableStateFlow<LinkedHashMap<K, Set<V>>>.updateMappedValueById(
    key: K,
    updatedItem: V,
    keySelector: (V) -> K,
    updateItem: (new: V, old: V) -> V = { new, _ -> new }
) {
    this.update { map ->
        map.apply {
            val currentSet = this[key].orEmpty()
            val oldItem = currentSet.first { keySelector(it) == keySelector(updatedItem) }
            val newSet = (currentSet - oldItem) + updateItem(updatedItem, oldItem)
            this[key] = newSet
        }
    }
}

fun <T> MutableStateFlow<List<T>>.updateListItemById(
    updatedItem: T, keySelector: (T) -> DBID,
    updateItem: (new: T, old: T) -> T = { new, _ -> new }
) {
    this.update { list ->
        list.map { item ->
            if (keySelector(item) == keySelector(updatedItem)) updateItem(
                updatedItem,
                item
            ) else item
        }
    }
}

fun <T> MutableStateFlow<List<T>>.updateListItemById(
    updatedItemId: DBID, keySelector: (T) -> DBID,
    updateItem: (old: T) -> T = { old -> old }
) {
    this.update { list ->
        list.map { item ->
            if (keySelector(item) == updatedItemId) updateItem(
                item,
            ) else item
        }
    }
}
