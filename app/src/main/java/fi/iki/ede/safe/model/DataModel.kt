package fi.iki.ede.safe.model

import android.util.Log
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.ui.utilities.firebaseLog
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object DataModel {
    // GPM Imports: READ DATA CLASSES BTW. collection may change on new imports
    private val _savedGPMsFlow = MutableStateFlow<Set<SavedGPM>>(emptySet())
    val savedGPMsFlow: StateFlow<Set<SavedGPM>> = _savedGPMsFlow.asStateFlow()


    private val _siteEntryToSavedGPM = LinkedHashMap<DecryptableSiteEntry, Set<SavedGPM>>()
    private val _siteEntryToSavedGPMFlow =
        MutableStateFlow(LinkedHashMap<DecryptableSiteEntry, Set<SavedGPM>>())
    val siteEntryToSavedGPMStateFlow: StateFlow<LinkedHashMap<DecryptableSiteEntry, Set<SavedGPM>>>
        get() = _siteEntryToSavedGPMFlow

    // Categories state and events
    val _categories =
        LinkedHashMap<DecryptableCategoryEntry, MutableList<DecryptableSiteEntry>>()
    private val _categoriesStateFlow = MutableStateFlow(_categories.keys.toList())
    val categoriesStateFlow: StateFlow<List<DecryptableCategoryEntry>> get() = _categoriesStateFlow

    // NO USE FOR NOW
    private val _categoriesSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.CategoryEvent>(extraBufferCapacity = 10, replay = 10)
    val categoriesSharedFlow: SharedFlow<PasswordSafeEvent.CategoryEvent> get() = _categoriesSharedFlow

    // SiteEntries state and events
    private val _siteEntriesStateFlow = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val siteEntriesStateFlow: StateFlow<List<DecryptableSiteEntry>> get() = _siteEntriesStateFlow

    // Test only now...
    private val _siteEntriesSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.SiteEntryEvent>(extraBufferCapacity = 10, replay = 10)
    val siteEntriesSharedFlow: SharedFlow<PasswordSafeEvent.SiteEntryEvent> get() = _siteEntriesSharedFlow

    private val _softDeletedStateFlow = MutableStateFlow<Set<DecryptableSiteEntry>>(emptySet())
    val softDeletedStateFlow: StateFlow<Set<DecryptableSiteEntry>> get() = _softDeletedStateFlow

    fun DecryptableSiteEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == categoryId }

    fun DecryptableCategoryEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == id }

    // no cost using (gets (encrypted) categories from memory)
    fun getCategories(): List<DecryptableCategoryEntry> = _categories.keys.toList()

    // no cost using (gets (encrypted) site entries from memory)
    fun getSiteEntries(): List<DecryptableSiteEntry> = _categories.values.flatten()

    // (almost) no cost using (gets (encrypted) categories' site entries from memory)
    fun getCategorysSiteEntries(categoryId: DBID): List<DecryptableSiteEntry> =
        _categories.filter { it.key.id == categoryId }.values.flatten()


    // TODO: used only while developing? or fixing broken imports?
    fun deleteAllSavedGPMs() {
        DBHelperFactory.getDBHelper().deleteAllSavedGPMs()
    }

    // TODO: RENAME
    suspend fun addOrEditCategory(
        category: DecryptableCategoryEntry,
        onAdd: suspend (DecryptableCategoryEntry) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            if (category.id == null) {
                // NEW
                category.id = db.addCategory(category)
                _categories[category] = mutableListOf()
                _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Added(category))
                onAdd(category)
            } else {
                db.updateCategory(category.id!!, category)

                // Update model
                val existingCategory = category.getCategory()
                val oldSiteEntries = _categories[existingCategory]
                val newUpdatedCategory = category.copy()
                newUpdatedCategory.containedSiteEntryCount = category.containedSiteEntryCount
                _categories.remove(existingCategory)
                _categories[newUpdatedCategory] = oldSiteEntries!!
                _categoriesSharedFlow.emit(
                    PasswordSafeEvent.CategoryEvent.Updated(
                        newUpdatedCategory
                    )
                )
            }
            _categoriesStateFlow.value = _categories.keys.toList()
        }
    }

    suspend fun deleteCategory(category: DecryptableCategoryEntry) {
        require(category.getCategory() == category) {
            "Alas category OBJECTS THEMSELVES are different, and SEARCH is needed"
        }
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            val rows = db.deleteCategory(category.id!!)

            // Update model
            _categories.remove(category)
            _categoriesStateFlow.value = _categories.keys.toList()
            _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Deleted(category))
        }
    }

    fun getSiteEntry(siteEntryId: DBID): DecryptableSiteEntry =
        getSiteEntries().first { it.id == siteEntryId }

    suspend fun addOrUpdateSiteEntry(
        siteEntry: DecryptableSiteEntry,
        onAdd: suspend (DecryptableSiteEntry) -> Unit = {}
    ) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            val category = getCategories().first { it.id == siteEntry.categoryId }
            if (siteEntry.id == null) {
                // we're adding a new PWD
                siteEntry.id = db.addSiteEntry(siteEntry)

                // Update model
                _categories[category]!!.add(siteEntry)
                val newCategory =
                    updateCategoriesSiteEntryCount(
                        category,
                        category.containedSiteEntryCount + 1,
                    )
                _siteEntriesSharedFlow.emit(
                    PasswordSafeEvent.SiteEntryEvent.Added(
                        newCategory,
                        siteEntry
                    )
                )
                onAdd(siteEntry)
            } else {
                db.updateSiteEntry(siteEntry)

                // Update model
                val siteEntries = _categories[category]
                val index = siteEntries!!.indexOfFirst { it.id == siteEntry.id }
                siteEntries[index] = siteEntry
                _siteEntriesSharedFlow.emit(
                    PasswordSafeEvent.SiteEntryEvent.Updated(
                        category,
                        siteEntry
                    )
                )
            }
            _siteEntriesStateFlow.value = _categories.values.flatten()
        }
    }

    suspend fun moveSiteEntry(
        siteEntry: DecryptableSiteEntry,
        targetCategory: DecryptableCategoryEntry
    ) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
        CoroutineScope(Dispatchers.IO).launch {
            val db = DBHelperFactory.getDBHelper()
            db.updateSiteEntryCategory(siteEntry.id!!, targetCategory.id!!)

            // Update model
            val oldCategory = siteEntry.getCategory()
            val updatedOldCategory = updateCategoriesSiteEntryCount(
                oldCategory,
                oldCategory.containedSiteEntryCount - 1,
            )

            // We might be called from different context with different copy
            // Lets find correct instance
            val pwdToRemove = _categories[updatedOldCategory]!!.first { it.id == siteEntry.id }
            _categories[updatedOldCategory]!!.remove(pwdToRemove)

            val copyOfSiteEntry = pwdToRemove.copy().apply {
                categoryId = targetCategory.id
            }

            val updatedTargetCategory = updateCategoriesSiteEntryCount(
                targetCategory,
                targetCategory.containedSiteEntryCount + 1,
            )

            _categories[updatedTargetCategory]!!.add(copyOfSiteEntry)
            _categoriesStateFlow.value = _categories.keys.toList()
            _siteEntriesStateFlow.value = _categories.values.flatten()
            _siteEntriesSharedFlow.emit(
                PasswordSafeEvent.SiteEntryEvent.Removed(
                    updatedTargetCategory,
                    copyOfSiteEntry
                )
            )
            _siteEntriesSharedFlow.emit(
                PasswordSafeEvent.SiteEntryEvent.Added(
                    updatedTargetCategory,
                    copyOfSiteEntry
                )
            )
        }
    }

    fun emptyAllSoftDeleted(ids: Set<DBID>) {
        val db = DBHelperFactory.getDBHelper()
        ids.forEach { id ->
            db.hardDeleteSiteEntry(id)
            _softDeletedStateFlow.value -= _softDeletedStateFlow.value.filter { entry -> id == entry.id!! }
        }
    }

    fun restoreSiteEntry(siteEntry: DecryptableSiteEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            _softDeletedStateFlow.value -= _softDeletedStateFlow.value.filter { it.id == siteEntry.id }
            // Find category this password belong to, or first category if it doesn't exist
            val category = getCategories().firstOrNull { it.id == siteEntry.categoryId }
                ?: getCategories().firstOrNull()
            // or at worst, we have no categories..in which case restoration is impossible
            if (category != null) {
                val db = DBHelperFactory.getDBHelper()
                siteEntry.categoryId = category.id
                siteEntry.deleted = 0
                db.restoreSoftDeletedSiteEntry(siteEntry.id!!)
                _categories[category]!!.add(siteEntry)
                _siteEntriesStateFlow.value += siteEntry
                //.value = _categories.values.flatten()
            }
        }
    }

    suspend fun deleteSiteEntry(siteEntry: DecryptableSiteEntry) {
        require(siteEntry.categoryId != null) { "SiteEntry's category must be known" }
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

            // Update model
            val category = siteEntry.getCategory()
            val updatedCategory =
                updateCategoriesSiteEntryCount(
                    category,
                    category.containedSiteEntryCount - 1,
                )
            val pwdToDelete = _categories[updatedCategory]!!.first { it.id == siteEntry.id }
            _categories[updatedCategory]!!.remove(pwdToDelete)
            _siteEntriesStateFlow.value = _categories.values.flatten()
            _siteEntriesSharedFlow.emit(
                PasswordSafeEvent.SiteEntryEvent.Removed(
                    updatedCategory,
                    pwdToDelete
                )
            )
        }
    }

    // horrible hack required as Flow objects need to be immutable
    // changing property is not reflected in the UI, NEW object is required
    private suspend fun updateCategoriesSiteEntryCount(
        category: DecryptableCategoryEntry,
        newSiteEntryCount: Int,
    ): DecryptableCategoryEntry {
        val copyOfCategory = category.copy().apply {
            containedSiteEntryCount = newSiteEntryCount
        }

        val oldSiteEntries = _categories[category]
        _categories.remove(category)
        _categories[copyOfCategory] = oldSiteEntries!!

        // TODO: Remove! Use THE stateflow? This is in search,THis provides CALMER updates than .value= rump'em all
        _categoriesStateFlow.update {
            it.map { cat ->
                if (cat.id == category.id) {
                    copyOfCategory
                } else cat
            }
        }
        _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Updated(copyOfCategory))
        return copyOfCategory
    }

    private fun syncLoadGPMsFromDB() {
        //_savedGPMsFlow.update { currentList -> currentList + listOfNewItems }
        _savedGPMsFlow.value = DBHelperFactory.getDBHelper().fetchSavedGPMsFromDB()
    }

    fun markSavedGPMIgnored(savedGpmId: Long) {
        DBHelperFactory.getDBHelper().markSavedGPMIgnored(savedGpmId)
        _savedGPMsFlow.update { currentList ->
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
    var softDeletedMaxAgeProvider: () -> Int = { Preferences.getSoftDeleteDays() }

    suspend fun loadFromDatabase() {
        _categories.clear()

        fun syncLoadCategoriesFromDB() {
            val db = DBHelperFactory.getDBHelper()
            val categories = db.fetchAllCategoryRows()

            // Update model
            // Quickly update all categories first and then the slow one..all the siteentries of all the categories...
            categories.forEach { category ->
                _categories[category] = mutableListOf()
            }
            _categoriesStateFlow.value = _categories.keys.toList()
        }

        fun syncLoadLinkedGPMs() {
            val db = DBHelperFactory.getDBHelper()
            val siteEntryIdGpmIdMappings = db.fetchAllSiteEntryGPMMappings()

            val savedGpms = _savedGPMsFlow.value
            val q = siteEntryIdGpmIdMappings.map { it ->
                getSiteEntry(it.key) to it.value.map { linkedGpmId -> savedGpms.firstOrNull { savedGpm -> linkedGpmId == savedGpm.id } }
                    .filterNotNull().toSet()
            }.toMap()
            _siteEntryToSavedGPM.putAll(q)
            _siteEntryToSavedGPMFlow.value = _siteEntryToSavedGPM
        }

        suspend fun syncLoadSiteEntriesFromDB() {
            val db = DBHelperFactory.getDBHelper()
            val catKeys = _categories.keys.toList()
            catKeys.forEach { category ->
                val categoriesSiteEntries = db.fetchAllRows(categoryId = category.id as DBID)

                // Update model
                _categories[category]!!.addAll(categoriesSiteEntries)
                val newCategory = updateCategoriesSiteEntryCount(
                    category,
                    categoriesSiteEntries.count(),
                )
            }
            _siteEntriesStateFlow.value = _categories.values.flatten()
        }

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
                    db.hardDeleteSiteEntry(it.id!!)
                    true
                } else false
            }.toSet()
            _softDeletedStateFlow.value = softDeletedSiteEntries - expiredSoftDeletedSiteEntries
        }

        // NOTE: Made a HUGE difference in display speed for 300+ siteEntries list on galaxy S24, if completed this is instantaneous
        // NOTE: This can ONLY succeed if user has logged in - as it sits now, this is the case, we load the data model only after login
        // Even if descriptions of site entries aren't very sensitive
        // all external access is kept encrypted, this though slows down UI visuals
        // In memory copy of the description is plain text, let's just decrypt them
        suspend fun launchDecryptDescriptions() {
            coroutineScope {
                launch(Dispatchers.Default) {
                    try {
                        _categories.values.forEach { siteEntries ->
                            siteEntries.forEach { encryptedSiteEntry ->
                                val noop = encryptedSiteEntry.cachedPlainDescription
                            }
                        }
                    } catch (ex: Exception) {
                        Log.d(TAG, "Cache warmup failed")
                    }
                }
            }
        }

        syncLoadCategoriesFromDB()
        syncLoadGPMsFromDB()
        syncLoadSiteEntriesFromDB()
        syncLoadLinkedGPMs()
        syncLoadSoftDeletedSiteEntries()
        launchDecryptDescriptions()

        if (BuildConfig.DEBUG) {
            dumpStrays()
        }
    }

    private suspend fun dumpStrays() {
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
                    filterAList(_categories.values.flatten(), _categories.keys.toList())

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
                    for (category in _categories.keys) {
                        println("Category id=${category.id} plainname=${category.plainName}") // OK: Dump
                        for (siteEntry in getCategorysSiteEntries(category.id!!)) {
                            println("  SiteEntry id=${siteEntry.id}, description=${siteEntry.cachedPlainDescription},changed=${siteEntry.passwordChangedDate}") // OK: Dump
                        }
                    }
                }
            }
        }
    }

    fun linkSaveGPMAndSiteEntry(siteEntry: DecryptableSiteEntry, savedGpmId: Long) {
        val gpm = _savedGPMsFlow.value.firstOrNull { it.id == savedGpmId }
        require(gpm != null) { "GPM not found by id $savedGpmId" }
        val siteEntryIndex = _siteEntryToSavedGPM.keys.firstOrNull { it.id == siteEntry.id }
        if (siteEntryIndex == null) {
            _siteEntryToSavedGPM[siteEntry] = setOf(gpm)
        } else {
            _siteEntryToSavedGPM[siteEntryIndex] =
                _siteEntryToSavedGPM[siteEntryIndex]!!.toSet() + setOf(gpm)
        }
        _siteEntryToSavedGPMFlow.value = _siteEntryToSavedGPM

        try {
            DBHelperFactory.getDBHelper().linkSaveGPMAndSiteEntry(siteEntry.id!!, savedGpmId)
        } catch (ex: Exception) {
            firebaseRecordException(
                "Linking SE=${siteEntry.id} and GPM=${savedGpmId} failed (exists already?) should never happend",
                ex
            )
        }
    }

    suspend fun addGpmAsSiteEntry(
        savedGpmId: Long,
        categoryId: DBID,
        onAdd: suspend (DecryptableSiteEntry) -> Unit
    ) {
        val gpm = _savedGPMsFlow.value.firstOrNull { it.id == savedGpmId }
        if (gpm == null) {
            firebaseLog("Trying to add non existing GPM $savedGpmId")
            return
        }
        addOrUpdateSiteEntry(DecryptableSiteEntry(categoryId).apply {
            username = gpm.encryptedUsername
            password = gpm.encryptedPassword
            website = gpm.encryptedUrl
            description = gpm.encryptedName
            note = gpm.encryptedNote
        }, onAdd)
    }

    fun getLinkedGPMs(siteEntryID: DBID): Set<SavedGPM> =
        _siteEntryToSavedGPM.filterKeys { it.id == siteEntryID }.values.flatten().toSet()

    fun getAllSiteEntryExtensions(ignoreId: DBID? = null): Map<SiteEntryExtensionType, Set<String>> =
        getSiteEntries().filter { it.id != ignoreId }.flatMap { it.extensions.entries }
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
        syncLoadGPMsFromDB()
        _siteEntryToSavedGPMFlow.value = _siteEntryToSavedGPM
    }

    private const val TAG = "DataModel"
}