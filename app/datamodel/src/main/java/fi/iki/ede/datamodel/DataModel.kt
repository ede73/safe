package fi.iki.ede.datamodel

import android.util.Log
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.preferences.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object DataModel {
    init {
        if (BuildConfig.DEBUG) {
            setupDebugStateflowObserver()
        }
    }

    private fun setupDebugStateflowObserver() {
        val tag = "DataModel(observer)"
        // this is just for debug build..
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            _siteEntriesStateFlow.collect { list ->
                Log.d(
                    tag,
                    "Debug observer: _siteEntriesStateFlow:  (${
                        list.map { it.id }.joinToString(",")
                    })"
                )
            }
        }
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            _categoriesStateFlow.collect { list ->
                Log.d(
                    tag,
                    "Debug observer: _categoriesStateFlow:  (${
                        list.map { it.id }.joinToString(",")
                    })"
                )
            }
        }
    }

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


    // TODO: RENAME
    fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit = {}
    ) {
        Preferences.setLastModified()
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
        Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            DBHelperFactory.getDBHelper().deleteCategory(category.id!!)
            _categoriesStateFlow.update { oldMap -> oldMap.filterNot { it.id == category.id } }
        }
    }

    fun getSiteEntry(siteEntryId: DBID): DecryptableSiteEntry =
        _siteEntriesStateFlow.value.first { it.id == siteEntryId }

    fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit = {}
    ) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        Preferences.setLastModified()
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
        Preferences.setLastModified()
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
        Preferences.setLastModified()
        val db = DBHelperFactory.getDBHelper()
        ids.forEach { id ->
            db.hardDeleteSiteEntry(id)
            _softDeletedStateFlow.value -= _softDeletedStateFlow.value.filter { entry -> id == entry.id!! }
        }
    }

    fun restoreSiteEntry(siteEntry: DecryptableSiteEntry) {
        Preferences.setLastModified()
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
        Preferences.setLastModified()
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            Preferences.getSoftDeleteDays().let {
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

    // contraption just for unit tests
    var softDeletedMaxAgeProvider: () -> Int =
        { Preferences.getSoftDeleteDays() }

    private fun storeAllExtensionsToPreferences() {
        Preferences.storeAllExtensions(getAllSiteEntryExtensions().keys)
    }

    suspend fun loadFromDatabase(loadExternalDatabases: () -> Unit) {
        _categoriesStateFlow.value = emptyList()
        _siteEntriesStateFlow.value = emptyList()
        _softDeletedStateFlow.value = emptySet()

        fun syncLoadSoftDeletedSiteEntries() {
            val db = DBHelperFactory.getDBHelper()
            val softDeletedSiteEntries = db.fetchAllRows(null, true).toSet()
            // are we past deletion time here?
            val softDeletedMaxAge = softDeletedMaxAgeProvider()
            val expiredSoftDeletedSiteEntries = softDeletedSiteEntries.filter {
                val softDeletedAge = DateUtils.getPeriodBetweenDates(
                    ZonedDateTime.now(),
                    DateUtils.unixEpochSecondsToLocalZonedDateTime(it.deleted),
                )
                if (softDeletedAge.days > softDeletedMaxAge) {
                    Preferences.setLastModified()
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
                        .forEach { (categoryId, siteEntries) ->
                            _categoriesStateFlow.updateListItemById(categoryId!!,
                                keySelector = { it.id!! }) {
                                it.apply {
                                    containedSiteEntryCount = siteEntries.size
                                }.copy()
                            }
                        }
                    storeAllExtensionsToPreferences()
                }
                launch { syncLoadSoftDeletedSiteEntries() }
                awaitAll(cats, sites)

                launch { syncLoadAllPhotos() }

                launch {
                    if (BuildConfig.DEBUG) {
                        dumpStrayCategoriesSiteEntries()
                    }
                }
                loadExternalDatabases()
            }
        }
    }

    // TODO: Horrible! Make it load photos in one go instead of polling every entry
    // Ie. fetch photo IDs first, then then one by one (else cursor will run out of mem)
    private fun syncLoadAllPhotos() {
        val dbHelper = DBHelperFactory.getDBHelper()
        _siteEntriesStateFlow.value.forEach { siteEntry ->
            firebaseTry {
                val photo = dbHelper.fetchPhotoOnly(siteEntry.id as DBID)
                if (photo != null) {
                    siteEntry.photo = photo
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

    suspend fun dumpModelInDebugMode() {
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

    fun getAllSiteEntryExtensions(ignoreId: DBID? = null): Map<String, Set<String>> =
        _siteEntriesStateFlow.value.filter { it.id != ignoreId }
            .flatMap { it.plainExtensions.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten().filterNot(String::isBlank).toSet() }

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
