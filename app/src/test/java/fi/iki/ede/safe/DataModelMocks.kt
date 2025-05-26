package fi.iki.ede.safe

import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmdatamodel.db.GPMDB
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockkClass
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

object DataModelMocks {
    private var masterKeyStore: Pair<Salt, IVCipherText>? = null

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
        val siteEntry = DecryptableSiteEntry(categoryId)
        siteEntry.id = id
        siteEntry.description = ks.encryptByteArray(description.toByteArray())
        siteEntry.username = ks.encryptByteArray(username.toByteArray())
        siteEntry.website = ks.encryptByteArray(website.toByteArray())
        siteEntry.note = ks.encryptByteArray(note.toByteArray())
        siteEntry.password = ks.encryptByteArray(password.toByteArray())
        if (changedDate != null) {
            siteEntry.passwordChangedDate = changedDate
            //ZonedDateTime.of(2023, 6, 17, 2, 3, 4, 0, ZoneId.of("UTC"))
        }
        return siteEntry
    }

    /**
     * This actually MOCKS the DB instead (as that is the source input of the datamodel
     */
    fun mockDataModelFor_UNIT_TESTS_ONLY(
        fakeModel: LinkedHashMap<DecryptableCategoryEntry, List<DecryptableSiteEntry>>
    ): DBHelper {
        val siteEntryTable = linkedMapOf<DBID, DecryptableSiteEntry>()
        val categoryTable = linkedMapOf<DBID, DecryptableCategoryEntry>()
        val gpmTable = linkedMapOf<DBID, Set<SavedGPM>>()
        val gpmTable2SiteEntryLink = linkedMapOf<DBID, Set<DBID>>()

        for ((category, siteEntries) in fakeModel.entries) {
            require(category.id != null) { "When initializing, category ID must be preset" }
            categoryTable[category.id!!] = category
            for (siteEntry in siteEntries) {
                require(siteEntry.id != null) { "When initializing, siteEntry ID must be preset" }
                siteEntryTable[siteEntry.id!!] = siteEntry
            }
        }

        val db = mockkClass(DBHelper::class)
        require(isMockKMock(db)) { "Mocking failed somehow" }
        DBHelperFactory.initializeDatabase(db)
        mockkObject(GPMDB)
        every { db.readableDatabase } answers { _ -> mockkClass(SQLiteDatabase::class) }
        every { db.writableDatabase } answers { _ -> mockkClass(SQLiteDatabase::class) }
        every { db.addSiteEntry(any<DecryptableSiteEntry>()) } answers { _ ->
            val id: DBID =
                if (firstArg<DecryptableSiteEntry>().id != null) firstArg<DecryptableSiteEntry>().id!!
                else if (siteEntryTable.keys.isEmpty()) 1
                else siteEntryTable.keys.max() + 1
            val se = firstArg<DecryptableSiteEntry>()
            se.id = id
            siteEntryTable[id] = se
            id
        }

        every { db.updateSiteEntry(any<DecryptableSiteEntry>()) } answers { _ ->
            val id = firstArg<DecryptableSiteEntry>().id!!
            check(siteEntryTable.containsKey(id)) { "Updating siteEntry that does not exist" }
            siteEntryTable[id] = firstArg()
            id
        }

        every { db.addCategory(any<DecryptableCategoryEntry>()) } answers { _ ->
            val id: DBID = if (categoryTable.keys.isEmpty()) 1 else categoryTable.keys.max() + 1
            val c = firstArg<DecryptableCategoryEntry>()
            c.id = id
            categoryTable[id] = c
            id
        }

        every { db.updateCategory(any<DBID>(), any<DecryptableCategoryEntry>()) } answers { _ ->
            check(categoryTable.containsKey(firstArg())) { "Updating category that does not exist" }
            categoryTable[firstArg()] = secondArg()
            firstArg()
        }

        every { db.fetchAllCategoryRows(any<MutableStateFlow<List<DecryptableCategoryEntry>>>()) } answers { _ ->
            val flow = firstArg<MutableStateFlow<List<DecryptableCategoryEntry>>?>()
            flow?.value = categoryTable.values.toList()
            categoryTable.values.toList()
        }

        // TODO: implement properly!
        every { db.fetchPhotoOnly(any<DBID>()) } answers { _ ->
            null
        }

        // TODO: Doesn't handle soft deleted site entries
        every {
            db.fetchAllRows(
                any<DBID>(),
                any<Boolean>(),
                any<MutableStateFlow<List<DecryptableSiteEntry>>>()
            )
        } answers { _ ->
            val flow =
                thirdArg<MutableStateFlow<List<DecryptableSiteEntry>>?>()
            if (secondArg<Boolean>()) {
                flow?.value = emptyList()
                ArrayList(emptyList())
            } else if (firstArg<DBID?>() == null) {
                flow?.value = siteEntryTable.values.toList()
                ArrayList(siteEntryTable.values.toList())
            } else {
                val filtered = siteEntryTable.values.filter { it.categoryId == firstArg<DBID>() }
                    .toList()
                flow?.value = filtered
                ArrayList(filtered)
            }
        }

        every { db.storeSaltAndEncryptedMasterKey(any<Salt>(), any<IVCipherText>()) } answers { _ ->
            masterKeyStore = Pair(firstArg(), secondArg())
        }

        every { db.fetchSaltAndEncryptedMasterKey() } answers { _ ->
            require(masterKeyStore != null) { "Master key MUST have been set in the DataModelMocks" }
            masterKeyStore!!
        }

        // transaction support
        val siteEntriesTableBackup =
            linkedMapOf<DBID, DecryptableSiteEntry>()
        val categoryTableBackup = linkedMapOf<DBID, DecryptableCategoryEntry>()
        var masterKeyStoreBackup: Pair<Salt, IVCipherText>? = null
        val sql = mockkClass(SQLiteDatabase::class)
        var transactionSuccess = false
        var inTransaction = false
        every { db.beginRestoration() } answers {
            transactionSuccess = false
            inTransaction = true
            siteEntriesTableBackup.clear()
            siteEntriesTableBackup.putAll(siteEntryTable)
            siteEntryTable.clear()
            categoryTableBackup.clear()
            categoryTableBackup.putAll(categoryTable)
            categoryTable.clear()
            masterKeyStoreBackup = masterKeyStore
            masterKeyStore = null
            sql
        }
        every { sql.inTransaction() } answers { _ ->
            inTransaction
        }
        every { sql.setTransactionSuccessful() } answers { _ ->
            transactionSuccess = true
        }
        every { sql.endTransaction() } answers { _ ->
            inTransaction = false
            if (transactionSuccess) {
                // retain current tables
                siteEntriesTableBackup.clear()
                categoryTableBackup.clear()
                masterKeyStoreBackup = null
            } else {
                // we're rolling back!
                siteEntryTable.clear()
                siteEntryTable.putAll(siteEntriesTableBackup)
                siteEntriesTableBackup.clear()
                categoryTable.clear()
                categoryTable.putAll(categoryTableBackup)
                categoryTableBackup.clear()
                masterKeyStore = masterKeyStoreBackup
                masterKeyStoreBackup = null
            }
            transactionSuccess = false
        }

        every { GPMDB.addSavedGPM(any<SavedGPM>()) } answers { _ ->
            val id: DBID =
                if (firstArg<SavedGPM>().id != null) firstArg<SavedGPM>().id!!
                else if (gpmTable.keys.isEmpty()) 1
                else gpmTable.keys.max() + 1
            val s = firstArg<SavedGPM>()
            gpmTable[id] =
                gpmTable.getOrPut(id) { mutableSetOf() }.toMutableSet() + s.copy(id = id)
            id
        }
        // GPMs (partial TODO:)
        every {
            GPMDB.fetchAllSavedGPMsFromDB(
                any<MutableStateFlow<Set<SavedGPM>>>()
            )
        } answers { _ ->
            val flow = firstArg<MutableStateFlow<Set<SavedGPM>>?>()
            flow?.value = gpmTable.values.flatten().toSet()
            // TODO: weak model, should filter per SiteEntry in firstArg
            gpmTable.values.flatten().toSet()
        }
        every {
            GPMDB.linkSaveGPMAndSiteEntry(
                any<DBID>(),
                any<DBID>()
            )
        } answers { _ ->
            val seid = firstArg<DBID>()
            val gpmId = secondArg<DBID>()
            gpmTable2SiteEntryLink[seid] =
                gpmTable2SiteEntryLink.getOrDefault(seid, emptySet()).toMutableSet() + gpmId
            mockkClass(SQLiteDatabase::class)
        }

        every { GPMDB.fetchAllSiteEntryGPMMappings() } answers { _ ->
            gpmTable2SiteEntryLink.toMap()
        }

        DataModel.softDeletedMaxAgeProvider = { 0 }
        runBlocking {
            DataModel.loadFromDatabase {
                GPMDataModel.loadFromDatabase()
            }
        }
        return db
    }
}