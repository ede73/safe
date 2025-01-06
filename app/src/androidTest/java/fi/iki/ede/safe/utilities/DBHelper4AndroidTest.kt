package fi.iki.ede.safe.utilities

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import kotlinx.coroutines.runBlocking

object DBHelper4AndroidTest {

    private fun getDBHelper() = DBHelperFactory.getDBHelper()

    fun justStoreSaltAndMasterKey(
        initializeMasterKey: IVCipherText,//TODO: Should be SaltedEncryptedPassword these two
        initializeSalt: Salt,
    ) {
        this.storedSalt = initializeSalt
        this.storedMasterKey = initializeMasterKey
    }

    // you have to KEEP the readable database reference, else in memory database will be destroyed
    fun initializeEverything(context: Context): SQLiteDatabase {
        val dbHelper = DBHelperFactory.initializeDatabase(DBHelper(context, null, false))
        val writableDatabase = dbHelper.writableDatabase
        dbHelper.storeSaltAndEncryptedMasterKey(storedSalt!!, storedMasterKey!!)
        return writableDatabase!!
    }

    fun configureDefaultTestDataModelAndDB() {
        addCategory(DEFAULT_1ST_CATEGORY, addPasswords = {
            addPassword(DEFAULT_1ST_PASSWORD_OF_1ST_CATEGORY, it)
            addPassword(DEFAULT_2ND_PASSWORD_OF_1ST_CATEGORY, it)
        }).addCategory(DEFAULT_2ND_CATEGORY, addPasswords = {
            addPassword(DEFAULT_1ST_PASSWORD_OF_2ND_CATEGORY, it)
            addPassword(DEFAULT_2ND_PASSWORD_OF_2ND_CATEGORY, it)
        })
        // Also handle initializing the data model
        runBlocking { DataModel.loadFromDatabase() }

        assert(2 == DataModel.categoriesStateFlow.value.size) {
            "DataModel initialization failure, <> 2 categories"
        }
        assert(4 == DataModel.siteEntriesStateFlow.value.size) {
            "DataModel initialization failure, <> 4 site entries"
        }
    }

    fun addCategory(
        name: String,
        forceId: DBID? = null,
        addPasswords: (categoryId: DBID) -> Unit = { _ -> }
    ): DBHelper4AndroidTest {
        val catId = getDBHelper().addCategory(DecryptableCategoryEntry().apply {
            id = forceId
            encryptedName = getKeyStore().encryptByteArray(name.toByteArray())
        })
        require(catId > 0) { "In order to add passwords, you must specify a category id" }
        addPasswords(catId)
        return this
    }

    // streamline? allow override, but STILL check the mock
    private fun getKeyStore() = KeyStoreHelperFactory.getKeyStoreHelper().apply {
        require(this.isMock) { "You MUST have called mockKeyStore() before calling" }
    }

    private fun addPassword(
        description: String,
        forceCategoryId: DBID,
        forceId: DBID? = null,
    ): DBHelper4AndroidTest {
        val pwd = DecryptableSiteEntry(forceCategoryId).apply {
            id = forceId
            this.description = getKeyStore().encryptByteArray(description.toByteArray())
        }
        getDBHelper().addSiteEntry(pwd)
        return this
    }

    const val DEFAULT_1ST_CATEGORY = "one"
    const val DEFAULT_2ND_CATEGORY = "two"
    private const val DEFAULT_1ST_PASSWORD_OF_1ST_CATEGORY = "1st of $DEFAULT_1ST_CATEGORY"
    private const val DEFAULT_2ND_PASSWORD_OF_1ST_CATEGORY = "2nd of $DEFAULT_1ST_CATEGORY"
    private const val DEFAULT_1ST_PASSWORD_OF_2ND_CATEGORY = "1st of $DEFAULT_2ND_CATEGORY"
    private const val DEFAULT_2ND_PASSWORD_OF_2ND_CATEGORY = "2nd of $DEFAULT_2ND_CATEGORY"

    private var storedSalt: Salt? = null
    private var storedMasterKey: IVCipherText? = null
}