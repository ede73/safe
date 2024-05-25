package fi.iki.ede.safe

import android.database.sqlite.SQLiteDatabase
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.model.DataModel
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import java.time.ZonedDateTime

object DataModelMocks {
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
    ): DecryptablePasswordEntry {
        val passwordEntry = DecryptablePasswordEntry(categoryId)
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
    fun mockDataModel(
        fakeModel: LinkedHashMap<DecryptableCategoryEntry, List<DecryptablePasswordEntry>>
    ): DBHelper {

        val passwordTable = linkedMapOf<DBID, DecryptablePasswordEntry>()
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
        // FULL DB mock
        DataModel.attachDBHelper(db)
        //every { DBHelperFactory.getDBHelper(any()) } returns db
        val password = slot<DecryptablePasswordEntry>()
        every { db.addPassword(capture(password)) } answers {
            val id: DBID = if (passwordTable.keys.isEmpty()) 1 else passwordTable.keys.max() + 1
            passwordTable[id] = password.captured
            id
        }
        every { db.updatePassword(capture(password)) } answers {
            val id = password.captured.id!!
            check(passwordTable.containsKey(id)) { "Updating password that does not exist" }
            passwordTable[id] = password.captured
            id
        }
        val category = slot<DecryptableCategoryEntry>()
        every { db.addCategory(capture(category)) } answers {
            val id: DBID = if (categoryTable.keys.isEmpty()) 1 else categoryTable.keys.max() + 1
            categoryTable[id] = category.captured
            id
        }
        val catid = slot<DBID>()
        every { db.updateCategory(capture(catid), capture(category)) } answers {
            check(categoryTable.containsKey(catid.captured)) { "Updating category that does not exist" }
            categoryTable[catid.captured] = category.captured
            catid.captured
        }
        every { db.fetchAllCategoryRows() } answers { categoryTable.values.toList() }
        val maybeCatId = slot<DBID>()
        every { db.fetchAllRows(capture(maybeCatId)) } answers {
            if (maybeCatId.isNull) {
                passwordTable.values.toList()
            } else {
                passwordTable.values.filter { it.categoryId == maybeCatId.captured }.toList()
            }
        }

        val salt = slot<Salt>()
        val cipher = slot<IVCipherText>()
        var masterKeyStore: Pair<Salt, IVCipherText>? = null
        every { db.storeSaltAndEncryptedMasterKey(capture(salt), capture(cipher)) } answers {
            masterKeyStore = Pair(salt.captured, cipher.captured)
        }
        every { db.fetchSaltAndEncryptedMasterKey() } answers {
            masterKeyStore!!
        }

        // transaction support
        val passwordTableBackup = linkedMapOf<DBID, DecryptablePasswordEntry>()
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

        runBlocking {
            DataModel.loadFromDatabase()
        }
        return db
    }
}