package fi.iki.ede.safe.model

import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    // for testing(test mocking) (actually do I....any more!?)
//    private fun getModel(): LinkedHashMap<DecryptableCategoryEntry, MutableList<DecryptablePasswordEntry>> {
//        return _categoriesAndPasswordsHash
//    }

    // no cost using (gets (encrypted) categories from memory)
    fun getCategories(): List<DecryptableCategoryEntry> = _categories.keys.toList()

    // no cost using (gets (encrypted) passwords from memory)
    fun getPasswords(): List<DecryptablePasswordEntry> = _categories.values.flatten()

    // (almost) no cost using (gets (encrypted) categories' passwords from memory)
    fun getCategorysPasswords(categoryId: DBID): List<DecryptablePasswordEntry> =
        _categories.filter { it.key.id == categoryId }.values.flatten()

    private fun _returnCategoryById(categoryId: DBID): DecryptableCategoryEntry =
        _categories.keys.first { it.id == categoryId }

    // TODO: RENAME
    suspend fun addOrEditCategory(category: DecryptableCategoryEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            if (category.id == null) {
                // NEW
                category.id = db!!.addCategory(category)
                _categories[category] = mutableListOf()
                _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Added(category))
            } else {
                // No need to change anything in the list!? Lists category object isn't OUR object
                // SO I think we'd need to actualy COPY PARAMETERS
                db!!.updateCategory(category.id!!, category)

                val existingCategory = _categories.keys.first { it.id == category.id }
                val oldPasswords = _categories[existingCategory]
                category.containedPasswordCount = existingCategory.containedPasswordCount
                _categories.remove(existingCategory)
                _categories[category] = oldPasswords!!
                _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Updated(category))
            }
            _categoriesStateFlow.value = _categories.keys.toList()
        }
    }

    suspend fun deleteCategory(category: DecryptableCategoryEntry) {
        require(_returnCategoryById(category.id as DBID) == category) {
            "Alas category OBJECTS THEMSELVES are different, and SEARCH is needed"
        }
        CoroutineScope(Dispatchers.IO).launch {
            db!!.deleteCategory(category.id!!)
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

    suspend fun deletePassword(password: DecryptablePasswordEntry) {
        require(password.categoryId != null) { "Password's category must be known" }
        require(getPassword(password.id as DBID) == password) {
            "Alas password OBJECTS THEMSELVES are different, and SEARCH is needed"
        }
        CoroutineScope(Dispatchers.IO).launch {
            db!!.deletePassword(password.id!!)
            val category = _returnCategoryById(password.categoryId as DBID)
            val newCategory =
                updateCategoriesPasswordCount(
                    category,
                    category.containedPasswordCount - 1,
                )
            _categories[newCategory]!!.remove(password)
            _passwordsStateFlow.value = _categories.values.flatten()
            _passwordsSharedFlow.emit(
                PasswordSafeEvent.PasswordEvent.Removed(
                    newCategory,
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
        val newCat = DecryptableCategoryEntry().apply {
            id = category.id
            encryptedName = category.encryptedName
            containedPasswordCount = newPasswordCount
        }
        val oldPasswords = _categories[category]
        _categories.remove(category)
        _categories[newCat] = oldPasswords!!

        // THis provides CALMER updates than .value= rump'em all
        _categoriesStateFlow.update {
            it.map { cat ->
                if (cat.id == category.id) {
                    newCat
                } else cat
            }
        }
        //_categoriesStateFlow.value = _categories.keys.toList()
        _categoriesSharedFlow.emit(PasswordSafeEvent.CategoryEvent.Updated(newCat))
        return newCat
    }

    suspend fun loadFromDatabase() {
        // TODO:
        // withContext(Dispatchers.IO) {???
        _categories.clear()

        fun loadCategoriesFromDB() {
            val categories = db!!.fetchAllCategoryRows()
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
                _categories[category]!!.addAll(categoriesPasswords)
                val newCategory = updateCategoriesPasswordCount(
                    category,
                    categoriesPasswords.count(),
                )
            }
            _passwordsStateFlow.value = _categories.values.flatten()
        }

        loadCategoriesFromDB()
        loadPasswordsFromDB()

        if (BuildConfig.DEBUG) {
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


    fun attachDBHelper(dbHelper: DBHelper) {
        db = dbHelper
    }

    fun dump() {
        if (BuildConfig.DEBUG) {
            for (category in _categories.keys) {
                println("Category id=${category.id} plainname=${category.plainName}") // OK: Dump
                for (password in getCategorysPasswords(category.id!!)) {
                    println("  Password id=${password.id} plainname=${password.plainDescription}") // OK: Dump
                }
            }
        }
    }
}