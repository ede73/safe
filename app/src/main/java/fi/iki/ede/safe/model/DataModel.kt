package fi.iki.ede.safe.model

import android.util.Log
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


object DataModel {
    // Categories state and events
    val _categories =
        LinkedHashMap<DecryptableCategoryEntry, MutableList<DecryptableSiteEntry>>()
    private val _categoriesStateFlow = MutableStateFlow(_categories.keys.toList())
    val categoriesStateFlow: StateFlow<List<DecryptableCategoryEntry>> get() = _categoriesStateFlow

    // NO USE FOR NOW
    private val _categoriesSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.CategoryEvent>(extraBufferCapacity = 10, replay = 10)
    val categoriesSharedFlow: SharedFlow<PasswordSafeEvent.CategoryEvent> get() = _categoriesSharedFlow

    // Passwords state and events
    private val _passwordsStateFlow = MutableStateFlow<List<DecryptableSiteEntry>>(emptyList())
    val passwordsStateFlow: StateFlow<List<DecryptableSiteEntry>> get() = _passwordsStateFlow
    private val _passwordsSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.PasswordEvent>(extraBufferCapacity = 10, replay = 10)

    // Test only now...
    val passwordsSharedFlow: SharedFlow<PasswordSafeEvent.PasswordEvent> get() = _passwordsSharedFlow

    private var db: DBHelper? = null

    fun DecryptableSiteEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == categoryId }

    fun DecryptableCategoryEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == id }

    // no cost using (gets (encrypted) categories from memory)
    fun getCategories(): List<DecryptableCategoryEntry> = _categories.keys.toList()

    // no cost using (gets (encrypted) passwords from memory)
    fun getPasswords(): List<DecryptableSiteEntry> = _categories.values.flatten()

    // (almost) no cost using (gets (encrypted) categories' passwords from memory)
    fun getCategorysPasswords(categoryId: DBID): List<DecryptableSiteEntry> =
        _categories.filter { it.key.id == categoryId }.values.flatten()


    // TODO: RENAME
    suspend fun addOrEditCategory(category: DecryptableCategoryEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            if (category.id == null) {
                // NEW
                category.id = db!!.addCategory(category)
                _categories[category] = mutableListOf()
                _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Added(category))
            } else {
                db!!.updateCategory(category.id!!, category)

                // Update model
                val existingCategory = category.getCategory()
                val oldPasswords = _categories[existingCategory]
                val newUpdatedCategory = category.copy()
                newUpdatedCategory.containedPasswordCount = category.containedPasswordCount
                _categories.remove(existingCategory)
                _categories[newUpdatedCategory] = oldPasswords!!
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
            db!!.deleteCategory(category.id!!)

            // Update model
            _categories.remove(category)
            _categoriesStateFlow.value = _categories.keys.toList()
            _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Deleted(category))
        }
    }

    fun getPassword(passwordId: DBID): DecryptableSiteEntry =
        getPasswords().first { it.id == passwordId }

    suspend fun addOrUpdatePassword(password: DecryptableSiteEntry) {
        require(password.categoryId != null) { "Password's category must be known" }
        CoroutineScope(Dispatchers.IO).launch {
            val category = getCategories().first { it.id == password.categoryId }
            if (password.id == null) {
                // we're adding a new PWD
                password.id = db!!.addPassword(password)

                // Update model
                _categories[category]!!.add(password)
                val newCategory =
                    updateCategoriesPasswordCount(
                        category,
                        category.containedPasswordCount + 1,
                    )
                _passwordsSharedFlow.emit(
                    PasswordSafeEvent.PasswordEvent.Added(
                        newCategory,
                        password
                    )
                )
            } else {
                db!!.updatePassword(password)

                // Update model
                val passwords = _categories[category]
                val index = passwords!!.indexOfFirst { it.id == password.id }
                passwords[index] = password
                _passwordsSharedFlow.emit(
                    PasswordSafeEvent.PasswordEvent.Updated(
                        category,
                        password
                    )
                )
            }
            _passwordsStateFlow.value = _categories.values.flatten()
        }
    }

    suspend fun movePassword(
        password: DecryptableSiteEntry,
        targetCategory: DecryptableCategoryEntry
    ) {
        require(password.categoryId != null) { "Password's category must be known" }
        CoroutineScope(Dispatchers.IO).launch {
            db!!.updatePasswordCategory(password.id!!, targetCategory.id!!)

            // Update model
            val oldCategory = password.getCategory()
            val updatedOldCategory = updateCategoriesPasswordCount(
                oldCategory,
                oldCategory.containedPasswordCount - 1,
            )

            // We might be called from different context with different copy
            // Lets find correct instance
            val pwdToRemove = _categories[updatedOldCategory]!!.first { it.id == password.id }
            _categories[updatedOldCategory]!!.remove(pwdToRemove)

            val copyOfPassword = pwdToRemove.copy().apply {
                categoryId = targetCategory.id
            }

            val updatedTargetCategory = updateCategoriesPasswordCount(
                targetCategory,
                targetCategory.containedPasswordCount + 1,
            )

            _categories[updatedTargetCategory]!!.add(copyOfPassword)
            _categoriesStateFlow.value = _categories.keys.toList()
            _passwordsStateFlow.value = _categories.values.flatten()
            _passwordsSharedFlow.emit(
                PasswordSafeEvent.PasswordEvent.Removed(
                    updatedTargetCategory,
                    copyOfPassword
                )
            )
            _passwordsSharedFlow.emit(
                PasswordSafeEvent.PasswordEvent.Added(
                    updatedTargetCategory,
                    copyOfPassword
                )
            )
        }
    }

    suspend fun deletePassword(password: DecryptableSiteEntry) {
        require(password.categoryId != null) { "Password's category must be known" }
        CoroutineScope(Dispatchers.IO).launch {
            db!!.deletePassword(password.id!!)

            // Update model
            val category = password.getCategory()
            val updatedCategory =
                updateCategoriesPasswordCount(
                    category,
                    category.containedPasswordCount - 1,
                )
            val pwdToDelete = _categories[updatedCategory]!!.first { it.id == password.id }
            _categories[updatedCategory]!!.remove(pwdToDelete)
            _passwordsStateFlow.value = _categories.values.flatten()
            _passwordsSharedFlow.emit(
                PasswordSafeEvent.PasswordEvent.Removed(
                    updatedCategory,
                    pwdToDelete
                )
            )
        }
    }

    // horrible hack required as Flow objects need to be immutable
    // changing property is not reflected in the UI, NEW object is required
    private suspend fun updateCategoriesPasswordCount(
        category: DecryptableCategoryEntry,
        newPasswordCount: Int,
    ): DecryptableCategoryEntry {
        val copyOfCategory = category.copy().apply {
            containedPasswordCount = newPasswordCount
        }

        val oldPasswords = _categories[category]
        _categories.remove(category)
        _categories[copyOfCategory] = oldPasswords!!

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

    suspend fun loadFromDatabase() {
        // TODO:
        // withContext(Dispatchers.IO) {???
        _categories.clear()

        fun loadCategoriesFromDB() {
            val categories = db!!.fetchAllCategoryRows()

            // Update model
            // Quickly update all categories first and then the slow one..all the passwords of all the categories...
            categories.forEach { category ->
                _categories[category] = mutableListOf()
            }
            _categoriesStateFlow.value = _categories.keys.toList()
        }

        suspend fun loadSiteEntriesFromDB() {
            // passwords..
            // TODO: This is NOT mocked at the moment
            //val passwords = db!!.fetchAllRows()
            val catKeys = _categories.keys.toList()
            catKeys.forEach { category ->
                val categoriesPasswords = db!!.fetchAllRows(categoryId = category.id as DBID)

                // Update model
                _categories[category]!!.addAll(categoriesPasswords)
                val newCategory = updateCategoriesPasswordCount(
                    category,
                    categoriesPasswords.count(),
                )
            }
            _passwordsStateFlow.value = _categories.values.flatten()
        }

        // NOTE: Made a HUGE difference in display speed for 300+ password list on galaxy S25, if completed this is instantaneous
        // NOTE: This can ONLY succeed if user has logged in - as it sits now, this is the case, we load the data model only after login
        // Even if descriptions of password entries aren't very sensitive
        // all external access is kept encrypted, this though slows down UI visuals
        // In memory copy of the description is plain text, let's just decrypt them
        suspend fun launchDecryptDescriptions() {
            coroutineScope {
                launch(Dispatchers.Default) {
                    try {
                        _categories.values.forEach { passwords ->
                            passwords.forEach { encryptedPassword ->
                                val noop = encryptedPassword.plainDescription
                            }
                        }
                    } catch (ex: Exception) {
                        Log.d(TAG, "Cache warmup failed")
                    }
                }
            }
        }

        loadCategoriesFromDB()
        loadSiteEntriesFromDB()
        launchDecryptDescriptions()

        if (BuildConfig.DEBUG) {
            dumpStrays()
        }
    }

    private suspend fun dumpStrays() {
        coroutineScope {
            launch {
                // now kinda interesting integrity verification, do we have stray passwords?
                // ie. belonging to categories nonexistent
                fun filterAList(
                    aList: List<DecryptableSiteEntry>,
                    bList: List<DecryptableCategoryEntry>
                ): List<DecryptableSiteEntry> {
                    val bIds = bList.map { it.id }.toSet()
                    return aList.filter { it.categoryId !in bIds }
                }

                val strayPasswords =
                    filterAList(_categories.values.flatten(), _categories.keys.toList())

                strayPasswords.forEach {
                    Log.e(
                        "DataModel",
                        "Stray password id=${it.id}, category=${it.categoryId}, description=${it.plainDescription}"
                    )
                }
            }
        }
    }

    fun attachDBHelper(dbHelper: DBHelper) {
        db = dbHelper
    }

    suspend fun dump() {
        if (BuildConfig.DEBUG) {
            coroutineScope {
                launch {
                    for (category in _categories.keys) {
                        println("Category id=${category.id} plainname=${category.plainName}") // OK: Dump
                        for (password in getCategorysPasswords(category.id!!)) {
                            println("  Password id=${password.id}, description=${password.plainDescription},changed=${password.passwordChangedDate}") // OK: Dump
                        }
                    }
                }
            }
        }
    }

    private const val TAG = "DataModel"
}