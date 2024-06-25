package fi.iki.ede.safe

import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

object DataModelMocks {
    var masterKeyStore: Pair<Salt, IVCipherText>? = null

    fun makeCat(
        categoryId: DBID?,
        ks: KeyStoreHelper,
        name: String = "encryptedcat${categoryId}"
    ): DecryptableCategoryEntry {
        val categoryEntry = DecryptableCategoryEntry()
        categoryEntry.encryptedName = ks.encryptByteArray(name.toByteArray())
        categoryEntry.id = categoryId
        return categoryEntry
    }

    fun makePwd(
        categoryId: DBID,
        id: DBID?,
        ks: KeyStoreHelper,
        description: String = "enc_desc${id}",
        website: String = "enc_web${id}",
        username: String = "enc_user${id}",
        password: String = "enc_pwd${id}",
        note: String = "enc_note${id}",
        changedDate: ZonedDateTime? = null
    ): DecryptableSiteEntry {
        val passwordEntry = DecryptableSiteEntry(categoryId)
        passwordEntry.id = id
        passwordEntry.description = ks.encryptByteArray(description.toByteArray())
        passwordEntry.username = ks.encryptByteArray(username.toByteArray())
        passwordEntry.website = ks.encryptByteArray(website.toByteArray())
        passwordEntry.note = ks.encryptByteArray(note.toByteArray())
        passwordEntry.password = ks.encryptByteArray(password.toByteArray())
        if (changedDate != null) {
            passwordEntry.passwordChangedDate = changedDate
            //ZonedDateTime.of(2023, 6, 17, 2, 3, 4, 0, ZoneId.of("UTC"))
        }
        return passwordEntry
    }

    /**
     * This actually MOCKS the DB instead (as that is the source input of the datamodel
     */
    fun mockDataModelFor_UNIT_TESTS_ONLY(
        fakeModel: LinkedHashMap<DecryptableCategoryEntry, List<DecryptableSiteEntry>>
    ): DBHelper {
        val passwordTable = linkedMapOf<DBID, DecryptableSiteEntry>()
        val categoryTable = linkedMapOf<DBID, DecryptableCategoryEntry>()
        for (categoryAndPasswords in fakeModel.entries) {
            require(categoryAndPasswords.key.id != null) { "When initializing, category ID must be preset" }
            categoryTable[categoryAndPasswords.key.id!!] = categoryAndPasswords.key
            for (password in categoryAndPasswords.value) {
                require(password.id != null) { "When initializing, password ID must be preset" }
                passwordTable[password.id!!] = password
            }
        }

        val db = mockkClass(DBHelper::class)
        require(isMockKMock(db)) { "Mocking failed somehow" }
        DBHelperFactory.initializeDatabase(db)

        // FULL DB mock
        //DataModel.attachDBHelper(db)
        //every { DBHelperFactory.getDBHelper(any()) } returns db
        every { db.addSiteEntry(any<DecryptableSiteEntry>()) } answers {
            val id: DBID = if (passwordTable.keys.isEmpty()) 1 else passwordTable.keys.max() + 1
            passwordTable[id] = firstArg()
            id
        }

        every { db.updateSiteEntry(any<DecryptableSiteEntry>()) } answers {
            val id = firstArg<DecryptableSiteEntry>().id!!
            check(passwordTable.containsKey(id)) { "Updating password that does not exist" }
            passwordTable[id] = firstArg()
            id
        }

        every { db.addCategory(any<DecryptableCategoryEntry>()) } answers {
            val id: DBID = if (categoryTable.keys.isEmpty()) 1 else categoryTable.keys.max() + 1
            categoryTable[id] = firstArg()
            id
        }

        every { db.updateCategory(any<DBID>(), any<DecryptableCategoryEntry>()) } answers {
            check(categoryTable.containsKey(firstArg())) { "Updating category that does not exist" }
            categoryTable[firstArg()] = secondArg()
            firstArg()
        }

        every { db.fetchAllCategoryRows() } answers { categoryTable.values.toList() }

        // TODO: Doesn't handle soft deleted site entries
        every { db.fetchAllRows(any<DBID>(), any<Boolean>()) } answers {
            if (secondArg<Boolean>()) {
                ArrayList(emptyList())
            } else if (firstArg<DBID?>() == null) {
                ArrayList(passwordTable.values.toList())
            } else {
                ArrayList(passwordTable.values.filter { it.categoryId == firstArg<DBID>() }
                    .toList())
            }
        }

        every { db.storeSaltAndEncryptedMasterKey(any<Salt>(), any<IVCipherText>()) } answers {
            masterKeyStore = Pair(firstArg(), secondArg())
        }

        every { db.fetchSaltAndEncryptedMasterKey() } answers {
            require(masterKeyStore != null) { "Master key MUST have been set in the DataModelMocks" }
            masterKeyStore!!
        }

        // transaction support
        val passwordTableBackup = linkedMapOf<DBID, DecryptableSiteEntry>()
        val categoryTableBackup = linkedMapOf<DBID, DecryptableCategoryEntry>()
        var masterKeyStoreBackup: Pair<Salt, IVCipherText>? = null
        val sql = mockkClass(SQLiteDatabase::class)
        var transactionSuccess = false
        var inTransaction = false
        every { db.beginRestoration() } answers {
            transactionSuccess = false
            inTransaction = true
            passwordTableBackup.clear()
            passwordTableBackup.putAll(passwordTable)
            passwordTable.clear()
            categoryTableBackup.clear()
            categoryTableBackup.putAll(categoryTable)
            categoryTable.clear()
            masterKeyStoreBackup = masterKeyStore
            masterKeyStore = null
            sql
        }
        every { sql.inTransaction() } answers {
            inTransaction
        }
        every { sql.setTransactionSuccessful() } answers {
            transactionSuccess = true
        }
        every { sql.endTransaction() } answers {
            inTransaction = false
            if (transactionSuccess) {
                // retain current tables
                passwordTableBackup.clear()
                categoryTableBackup.clear()
                masterKeyStoreBackup = null
            } else {
                // we're rolling back!
                passwordTable.clear()
                passwordTable.putAll(passwordTableBackup)
                passwordTableBackup.clear()
                categoryTable.clear()
                categoryTable.putAll(categoryTableBackup)
                categoryTableBackup.clear()
                masterKeyStore = masterKeyStoreBackup
                masterKeyStoreBackup = null
            }
            transactionSuccess = false
        }

        // GPMs (partial TODO:)
        every { db.fetchSavedGPMsFromDB() } returns emptySet()
        every { db.fetchAllSiteEntryGPMMappings() } returns emptyMap()

        DataModel.softDeletedMaxAgeProvider = { 0 }
        runBlocking {
            DataModel.loadFromDatabase()
        }
        return db
    }
}