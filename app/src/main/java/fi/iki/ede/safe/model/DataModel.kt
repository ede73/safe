package fi.iki.ede.safe.model

import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
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

// hot events not actually used...
sealed class PasswordSafeEvent {
    sealed class CategoryEvent : PasswordSafeEvent() {
        data class Added(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Deleted(val category: DecryptableCategoryEntry) : CategoryEvent()
        data class Updated(val category: DecryptableCategoryEntry) : CategoryEvent()
    }

    sealed class PasswordEvent : PasswordSafeEvent() {
        data class Added(
            val category: DecryptableCategoryEntry,
            val password: DecryptablePasswordEntry
        ) : PasswordEvent()

        data class Updated(
            val category: DecryptableCategoryEntry,
            val password: DecryptablePasswordEntry
        ) : PasswordEvent()

        data class Removed(
            val category: DecryptableCategoryEntry,
            val password: DecryptablePasswordEntry
        ) : PasswordEvent()
    }
}

object DataModel {
    // Categories state and events
    private val _categories =
        LinkedHashMap<DecryptableCategoryEntry, MutableList<DecryptablePasswordEntry>>()
    private val _categoriesStateFlow = MutableStateFlow(_categories.keys.toList())
    val categoriesStateFlow: StateFlow<List<DecryptableCategoryEntry>> get() = _categoriesStateFlow

    private val _categoriesSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.CategoryEvent>(extraBufferCapacity = 10, replay = 10)
    val categoriesSharedFlow: SharedFlow<PasswordSafeEvent.CategoryEvent> get() = _categoriesSharedFlow

    // Passwords state and events
    private val _passwordsStateFlow = MutableStateFlow<List<DecryptablePasswordEntry>>(emptyList())
    val passwordsStateFlow: StateFlow<List<DecryptablePasswordEntry>> get() = _passwordsStateFlow

    private val _passwordsSharedFlow =
        MutableSharedFlow<PasswordSafeEvent.PasswordEvent>(extraBufferCapacity = 10, replay = 10)

    // Test only now...
    val passwordsSharedFlow: SharedFlow<PasswordSafeEvent.PasswordEvent> get() = _passwordsSharedFlow

    private var db: DBHelper? = null

    fun DecryptablePasswordEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == categoryId }

    fun DecryptableCategoryEntry.getCategory(): DecryptableCategoryEntry =
        _categories.keys.first { it.id == id }

    // no cost using (gets (encrypted) categories from memory)
    fun getCategories(): List<DecryptableCategoryEntry> = _categories.keys.toList()

    // no cost using (gets (encrypted) passwords from memory)
    fun getPasswords(): List<DecryptablePasswordEntry> = _categories.values.flatten()

    // (almost) no cost using (gets (encrypted) categories' passwords from memory)
    fun getCategorysPasswords(categoryId: DBID): List<DecryptablePasswordEntry> =
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

    fun getPassword(passwordId: DBID): DecryptablePasswordEntry =
        getPasswords().first { it.id == passwordId }

    suspend fun addOrUpdatePassword(password: DecryptablePasswordEntry) {
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
        password: DecryptablePasswordEntry,
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

            _categories[updatedOldCategory]!!.remove(password)

            val copyOfPassword = password.copy().apply {
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

    suspend fun deletePassword(password: DecryptablePasswordEntry) {
        require(password.categoryId != null) { "Password's category must be known" }
        require(getPassword(password.id as DBID) == password) {
            "Alas password OBJECTS THEMSELVES are different, and SEARCH is needed"
        }
        CoroutineScope(Dispatchers.IO).launch {
            db!!.deletePassword(password.id!!)

            // Update model
            val category = password.getCategory()
            val updatedCategory =
                updateCategoriesPasswordCount(
                    category,
                    category.containedPasswordCount - 1,
                )
            _categories[updatedCategory]!!.remove(password)
            _passwordsStateFlow.value = _categories.values.flatten()
            _passwordsSharedFlow.emit(
                PasswordSafeEvent.PasswordEvent.Removed(
                    updatedCategory,
                    password
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

        // THis provides CALMER updates than .value= rump'em all
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

        suspend fun loadPasswordsFromDB() {
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
        loadPasswordsFromDB()
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
                    aList: List<DecryptablePasswordEntry>,
                    bList: List<DecryptableCategoryEntry>
                ): List<DecryptablePasswordEntry> {
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
                            println("  Password id=${password.id} plainname=${password.plainDescription}") // OK: Dump
                        }
                    }
                }
            }
        }
    }

    private const val TAG = "DataModel"
}