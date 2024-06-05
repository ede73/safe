package fi.iki.ede.safe.utilities

import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking

object MockDBHelper {
    fun mockDBHelper(
        initializeMasterKey: IVCipherText,//TODO: Should ne SaltedEncryptedPassword these two
        initializeSalt: Salt,
        initialPasswords: List<DecryptableSiteEntry>,
        initialCategories: List<DecryptableCategoryEntry>,
    ): DBHelper {
        require(!DBHelper.isMock) { "You should mock DBHelper just once (stern warning)" }
        require(!DBHelperFactory.isMock) { "You MUST NOT mock DBHelperFactory" }
        val db = mockk<DBHelper>()

        passwords.clear()
        categories.clear()
        passwords.addAll(initialPasswords)
        categories.addAll(initialCategories)

        storedSalt = initializeSalt
        storedMasterKey = initializeMasterKey
        every { db.fetchSaltAndEncryptedMasterKey() } answers {
            Pair(
                storedSalt!!,
                storedMasterKey!!
            )
        }

        every { db.close() } answers {}

        every { db.storeSaltAndEncryptedMasterKey(any(), any()) } answers {
            storedSalt = firstArg()
            storedMasterKey = secondArg()
            //Preferences.setMasterkeyInitialized()??
        }
        every { db.addCategory(any()) } answers {
            // we have ID or dont!
            val category: DecryptableCategoryEntry = firstArg()
            category.id = category.id ?: getNextFreeCategoryId()
            categories.add(category)
            category.id!!
        }

        every { db.deleteCategory(any()) } answers {
            val dbid: DBID = firstArg()
            categories.removeIf { it.id == dbid }
            1
        }
        every { db.updateCategory(any(), any()) } answers {
            val dbid: DBID = firstArg()
            val category: DecryptableCategoryEntry = secondArg()
            category.id = dbid
            categories.find { it.id == dbid }
                ?.let { categories[categories.indexOf(it)] = category }
            category.id!!
        }

        every { db.fetchAllCategoryRows() } answers { categories }
        every { db.getCategoryCount(any()) } answers { categories.size }

        every { db.fetchAllRows(any()) } answers {
            val categoryId: DBID? = firstArg()
            passwords.filter { password ->
                categoryId?.let { password.categoryId == categoryId } ?: true
            }
        }
        every { db.updatePassword(any()) } answers {
            val password: DecryptableSiteEntry = firstArg()
            passwords.find { it.id == password.id }
                ?.let { passwords[passwords.indexOf(it)] = password }
            password.id!!
        }
        every { db.updatePasswordCategory(any(), any()) } answers {
            val passwordId: DBID = firstArg()
            val newCat: DBID = firstArg()
            passwords.find { it.id == passwordId }?.let { it.categoryId = newCat }
            1
        }
        every { db.addPassword(any()) } answers {
            // we have ID or dont!
            val password: DecryptableSiteEntry = firstArg()
            password.id = password.id ?: getNextFreePasswordId()
            passwords.add(password)
            password.id!!
        }

        every { db.deletePassword(any()) } answers {
            val dbid: DBID = firstArg()
            passwords.removeIf { it.id == dbid }
            1
        }
        every { db.beginRestoration() } answers {
            throw Exception("Not done")
        }

        mockkObject(DBHelperFactory)
        every { DBHelperFactory.getDBHelper(any()) } returns db

        // Self assertion check that all functions
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dBInstance = DBHelperFactory.getDBHelper(context)
        assert(dBInstance == db) { "DBInstance mismatch" }
        val saltyMaster = dBInstance.fetchSaltAndEncryptedMasterKey()

        // TODO: some shit...
//        assertArrayEquals(masterKey().iv, saltyMaster.second.iv)
//        assertArrayEquals(masterKey().cipherText, saltyMaster.second.cipherText)

        return db
    }

    fun clearCategories(): MockDBHelper {
        categories.clear()
        return this
    }

    fun clearPasswords(): MockDBHelper {
        passwords.clear()
        return this
    }

    private fun getNextFreeCategoryId(): DBID =
        categories.maxOfOrNull { it.id as DBID }?.plus(1L) ?: 1L

    private fun getNextFreePasswordId(): DBID =
        passwords.maxOfOrNull { it.id as DBID }?.plus(1L) ?: 1L

    fun dump() {
        categories.forEach { cat ->
            println("Category id=${cat.id} name=${cat.plainName}")
        }
        passwords.forEach { p ->
            println("Password id=${p.id} catid=${p.categoryId} desc=${p.plainDescription}")
        }
    }

    fun addCategory(
        name: String,
        forceId: DBID? = null,
        addPasswords: (categoryId: DBID) -> Unit = { _ -> }
    ): MockDBHelper {
        val catId: DBID = forceId ?: getNextFreeCategoryId()
        categories.add(DecryptableCategoryEntry().apply {
            id = catId
            encryptedName = getKeyStore().encryptByteArray(name.toByteArray())
        })
        if (addPasswords != null) {
            addPasswords(catId)
        }
        return this
    }

    // streamline? allow override, but STILL check the mock
    private fun getKeyStore() = KeyStoreHelperFactory.getKeyStoreHelper().apply {
        require(this.isMock) { "You MUST have called mockKeyStore() before calling" }
    }

    fun addPassword(
        description: String,
        forceCategoryId: DBID? = null,
        forceId: DBID? = null,
    ): MockDBHelper {
        val catId: DBID = forceCategoryId ?: getNextFreeCategoryId()
        val passwordId: DBID = forceId ?: getNextFreePasswordId()
        passwords.add(DecryptableSiteEntry(catId).apply {
            id = passwordId
            categoryId = catId
            this.description = getKeyStore().encryptByteArray(description.toByteArray())
        })
        return this
    }

    fun isInitialized() = storedSalt != null && storedMasterKey != null

    fun initializeBasicTestDataModel() {
        require(DBHelperFactory.isMock) { "You MUST call MockDBHelper.mockDBHelper from @BeforeClass,@JvmStatic initializer" }
        clearCategories().clearPasswords()
        addCategory(DEFAULT_1ST_CATEGORY, addPasswords = {
            addPassword(DEFAULT_1ST_PASSWORD_OF_1ST_CATEGORY, it)
            addPassword(DEFAULT_2ND_PASSWORD_OF_1ST_CATEGORY, it)
        }).addCategory(DEFAULT_2ND_CATEGORY, addPasswords = {
            addPassword(DEFAULT_1ST_PASSWORD_OF_2ND_CATEGORY, it)
            addPassword(DEFAULT_2ND_PASSWORD_OF_2ND_CATEGORY, it)
        })
        // Also handle initializing the data model
        runBlocking { DataModel.loadFromDatabase() }

        assert(2 == DataModel.getCategories().size) {
            "DataModel initialization failure"
        }

    }

    const val DEFAULT_1ST_CATEGORY = "one"
    const val DEFAULT_2ND_CATEGORY = "two"
    const val DEFAULT_1ST_PASSWORD_OF_1ST_CATEGORY = "1st of $DEFAULT_1ST_CATEGORY"
    const val DEFAULT_2ND_PASSWORD_OF_1ST_CATEGORY = "2nd of $DEFAULT_1ST_CATEGORY"
    const val DEFAULT_1ST_PASSWORD_OF_2ND_CATEGORY = "1st of $DEFAULT_2ND_CATEGORY"
    const val DEFAULT_2ND_PASSWORD_OF_2ND_CATEGORY = "2nd of $DEFAULT_2ND_CATEGORY"

    val categories: MutableList<DecryptableCategoryEntry> = mutableListOf()
    private val passwords: MutableList<DecryptableSiteEntry> = mutableListOf()
    private var storedSalt: Salt? = null
    private var storedMasterKey: IVCipherText? = null
}