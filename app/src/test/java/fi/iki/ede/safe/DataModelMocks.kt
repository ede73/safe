package fi.iki.ede.safe

import fi.iki.ede.db.DBTransaction
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.DBID
import fi.iki.ede.gpm.model.*
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.gpmdatamodel.db.GPMDB
import io.mockk.coEvery
import io.mockk.every
import io.mockk.isMockKMock
import io.mockk.mockkClass
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

@ExperimentalTime
object DataModelMocks {
    private var masterKeyStore: Pair<Salt, IVCipherText>? = null

    fun makeCat(
        categoryId: DBID?,
        name: String = "encryptedcat${categoryId}"
    ): DecryptableCategoryEntry {
        val categoryEntry = DecryptableCategoryEntry()
        categoryEntry.encryptedName = name.encrypt()
        categoryEntry.id = categoryId
        return categoryEntry
    }

    fun makePwd(
        categoryId: DBID,
        id: DBID?,
        description: String = "enc_desc${id}",
        website: String = "enc_web${id}",
        username: String = "enc_user${id}",
        password: String = "enc_pwd${id}",
        note: String = "enc_note${id}",
        changedUtcDate: Instant? = null
    ): DecryptableSiteEntry {
        val siteEntry = DecryptableSiteEntry(categoryId)
        siteEntry.id = id
        siteEntry.description = description.encrypt()
        siteEntry.username = username.encrypt()
        siteEntry.website = website.encrypt()
        siteEntry.note = note.encrypt()
        siteEntry.password = password.encrypt()
        if (changedUtcDate != null) {
            siteEntry.passwordChangedDate = changedUtcDate
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

        val mockSafeDb = mockkClass(fi.iki.ede.db.SafeDatabase::class)
        val mockCxfAccountDao = mockkClass(fi.iki.ede.db.cxf.CxfAccountDao::class)
        val mockCxfImportDao = mockkClass(fi.iki.ede.db.cxf.CxfImportDao::class)
        val mockCxfPasskeyDao = mockkClass(fi.iki.ede.db.cxf.CxfPasskeyDao::class)

        val cxfAccountsList = mutableListOf<fi.iki.ede.db.cxf.CXFAccount>()
        val cxfImportsList = mutableListOf<fi.iki.ede.db.cxf.CXFImport>()
        val cxfPasskeysList = mutableListOf<fi.iki.ede.db.cxf.CXFPasskey>()

        coEvery { mockCxfAccountDao.getAll() } answers { cxfAccountsList.toList() }
        coEvery { mockCxfAccountDao.getByCxfAccountId(any()) } answers {
            val queryId = firstArg<String>()
            cxfAccountsList.firstOrNull { it.cxfAccountId == queryId }
        }
        coEvery { mockCxfAccountDao.insert(any()) } answers {
            val acc = firstArg<fi.iki.ede.db.cxf.CXFAccount>()
            val newId = (cxfAccountsList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = acc.copy(id = newId)
            cxfAccountsList.add(inserted)
            newId
        }

        coEvery { mockCxfImportDao.getAll() } answers { cxfImportsList.toList() }
        coEvery { mockCxfImportDao.getByAccountId(any()) } answers {
            val accId = firstArg<Long>()
            cxfImportsList.filter { it.accountId == accId }
        }
        coEvery { mockCxfImportDao.insert(any()) } answers {
            val item = firstArg<fi.iki.ede.db.cxf.CXFImport>()
            val newId = (cxfImportsList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = item.copy(id = newId)
            cxfImportsList.add(inserted)
            newId
        }

        coEvery { mockCxfPasskeyDao.getAll() } answers { cxfPasskeysList.toList() }
        coEvery { mockCxfPasskeyDao.getByAccountId(any()) } answers {
            val accId = firstArg<Long>()
            cxfPasskeysList.filter { it.accountId == accId }
        }
        coEvery { mockCxfPasskeyDao.insert(any()) } answers {
            val item = firstArg<fi.iki.ede.db.cxf.CXFPasskey>()
            val newId = (cxfPasskeysList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = item.copy(id = newId)
            cxfPasskeysList.add(inserted)
            newId
        }

        every { mockSafeDb.cxfAccountDao() } returns mockCxfAccountDao
        every { mockSafeDb.cxfImportDao() } returns mockCxfImportDao
        every { mockSafeDb.cxfPasskeyDao() } returns mockCxfPasskeyDao
        every { db.database } returns mockSafeDb

        DBHelperFactory.initializeDatabase(db)
        mockkObject(GPMDB)

        val mockSafeDb = mockkClass(fi.iki.ede.db.SafeDatabase::class)
        val mockCxfAccountDao = mockkClass(fi.iki.ede.db.cxf.CxfAccountDao::class)
        val mockCxfImportDao = mockkClass(fi.iki.ede.db.cxf.CxfImportDao::class)
        val mockCxfPasskeyDao = mockkClass(fi.iki.ede.db.cxf.CxfPasskeyDao::class)

        val cxfAccountsList = mutableListOf<fi.iki.ede.db.cxf.CXFAccount>()
        val cxfImportsList = mutableListOf<fi.iki.ede.db.cxf.CXFImport>()
        val cxfPasskeysList = mutableListOf<fi.iki.ede.db.cxf.CXFPasskey>()

        coEvery { mockCxfAccountDao.getAll() } answers { cxfAccountsList.toList() }
        coEvery { mockCxfAccountDao.getByCxfAccountId(any()) } answers {
            val queryId = firstArg<String>()
            cxfAccountsList.firstOrNull { it.cxfAccountId == queryId }
        }
        coEvery { mockCxfAccountDao.insert(any()) } answers {
            val acc = firstArg<fi.iki.ede.db.cxf.CXFAccount>()
            val newId = (cxfAccountsList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = acc.copy(id = newId)
            cxfAccountsList.add(inserted)
            newId
        }

        coEvery { mockCxfImportDao.getAll() } answers { cxfImportsList.toList() }
        coEvery { mockCxfImportDao.getByAccountId(any()) } answers {
            val accId = firstArg<Long>()
            cxfImportsList.filter { it.accountId == accId }
        }
        coEvery { mockCxfImportDao.insert(any()) } answers {
            val item = firstArg<fi.iki.ede.db.cxf.CXFImport>()
            val newId = (cxfImportsList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = item.copy(id = newId)
            cxfImportsList.add(inserted)
            newId
        }

        coEvery { mockCxfPasskeyDao.getAll() } answers { cxfPasskeysList.toList() }
        coEvery { mockCxfPasskeyDao.getByAccountId(any()) } answers {
            val accId = firstArg<Long>()
            cxfPasskeysList.filter { it.accountId == accId }
        }
        coEvery { mockCxfPasskeyDao.insert(any()) } answers {
            val item = firstArg<fi.iki.ede.db.cxf.CXFPasskey>()
            val newId = (cxfPasskeysList.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
            val inserted = item.copy(id = newId)
            cxfPasskeysList.add(inserted)
            newId
        }

        every { mockSafeDb.cxfAccountDao() } returns mockCxfAccountDao
        every { mockSafeDb.cxfImportDao() } returns mockCxfImportDao
        every { mockSafeDb.cxfPasskeyDao() } returns mockCxfPasskeyDao
        every { db.database } returns mockSafeDb
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
        val sql = mockkClass(DBTransaction::class)
        var transactionSuccess = false
        every { db.beginRestoration() } answers {
            transactionSuccess = false
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
        every { sql.setTransactionSuccessful() } answers { _ ->
            transactionSuccess = true
        }
        every { sql.endTransaction() } answers { _ ->
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