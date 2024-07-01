package fi.iki.ede.safe.db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.flow.MutableStateFlow

typealias DBID = Long

/**
 * Bug!? in SQLite? Do not use readableDatabase or writeableDatabase.use!
 * It will close the instrumentation test in-memory database unconditionally.
 * Even if you hold the database reference, you specifically increase reference
 * count by acquiring reference, you open a transaction and dont close it (yet).
 * What ever I tried, the WHOLE database gets close. And is with in-memory DBs
 * once closed, all data is lost :)
 */
class DBHelper internal constructor(
    context: Context,
    databaseName: String? = DATABASE_NAME,
    regularAppNotATest: Boolean = false
) :
    SQLiteOpenHelper(
        context, databaseName, null, DATABASE_VERSION,
        // Alas API 33:OpenParams.Builder().setJournalMode(JOURNAL_MODE_MEMORY).build()
    ) {
    init {
        if (!regularAppNotATest || System.getProperty("test") == "test") {
            // MUST be a test case
            require(databaseName == null) { "MUST BE InMemory DB" }
        } else {
            require(databaseName != null) { "Cannot use InMemory DB in prod" }
            require(System.getProperty("test") != "test") { "Test cannot use file DB" }
        }
    }

//    @OptIn(ExperimentalStdlibApi::class)
//    fun dumpInMemoryDb() {
//        return
//        val database = readableDatabase
//        val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
//        while (cursor.moveToNext()) {
//            val tableName = cursor.getString(0)
//            Log.d(TAG,"Table: $tableName")
//            val tableCursor = database.rawQuery(
//                "SELECT * FROM $tableName", null,
//            )
//            val columnCount = tableCursor.columnCount
//            while (tableCursor.moveToNext()) {
//                for (i in 0 until columnCount) {
//                    if (tableCursor.getType(i) == Cursor.FIELD_TYPE_BLOB) {
//                        print(tableCursor.getBlob(i).toHexString() + " | ")
//                    } else {
//                        print(tableCursor.getString(i) + " | ")
//                    }
//                }
//                Log.d(TAG,)
//            }
//            tableCursor.close()
//        }
//        cursor.close()
//    }

    private var onCreateCalled = false
    override fun onCreate(db: SQLiteDatabase?) {
        require(!onCreateCalled) { "ON create called TWICE" }
        onCreateCalled = true
        listOf(
            Category,
            SiteEntry,
            Keys,
            GooglePasswordManager,
            SiteEntry2GooglePasswordManager
        ).forEach {
            try {
                it.create().forEach { sql ->
                    db?.execSQL(sql)
                }
            } catch (ex: SQLiteException) {
                Log.e(TAG, "Error initializing database, sqliteVersion=${sqliteVersion()}", ex)
                throw ex
            }
        }
    }

    // called once (regardless of amount of upgrades needed)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // DO USE hardcoded strings for DB references, as it records history
        (oldVersion until newVersion).forEach { upgrade ->
            Log.i(TAG, "onUpgrade $upgrade (until $newVersion), sqlite ${sqliteVersion()}")
            when (upgrade) {
                0 -> {
                    // should never happen except maybe during upgrade test scenarios
                }

                1 -> upgradeFromV1ToV2AddPhoto(db, upgrade)

                2 -> {
                    // there's no drop column support prior to 3.50.0 - no harm leaving the column
                    // compared to alternative - full table recreation and copy
                    // Actually its buggy (potentially destructive) until 3.35.5
                    upgradeFromV2ToV3RemoveLastDateTimeEdit(db, upgrade)
                }

                3 -> {
                    upgradeFromV3ToV4MergeKeys(db, upgrade)
                }

                4 -> {
                    upgradeFromV4ToV5MergeKeys(db, upgrade)
                }

                5 -> {
                    upgradeFromV5ToV6AddDeletedColumn(db, upgrade)
                }

                6 -> {
                    upgradeFromV6ToV7AddExtensionsColumn(db, upgrade)
                }

                else -> Log.w(
                    TAG, "onUpgrade() with unknown oldVersion $oldVersion to $newVersion"
                )
            }
        }
    }

    // called once (regardless of amount of downgrades needed)
    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Downgrade $oldVersion to $newVersion")
    }

    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) {
        writableDatabase.apply {
            beginTransaction() // TODO: BAD, we have transaction in restore already
            delete(Keys).let {
                insert(
                    Keys,
                    ContentValues().apply {
                        put(Keys.Columns.ENCRYPTED_KEY, ivCipher)
                        put(Keys.Columns.SALT, salt.salt)
                    }
                )
            }

            setTransactionSuccessful()
            endTransaction()
        }
    }

    // TODO: Replace with SaltedEncryptedPassword (once it supports IVCipher)
    fun fetchSaltAndEncryptedMasterKey() =
        readableDatabase.let { db ->
            db.query(true, Keys, setOf(Keys.Columns.ENCRYPTED_KEY, Keys.Columns.SALT)).use {
                if (it.count > 0) {
                    it.moveToFirst()
                    val salt = it.getBlob(it.getColumnIndexOrThrow(Keys.Columns.SALT))
                    Pair(
                        Salt(salt),
                        IVCipherText(
                            CipherUtilities.IV_LENGTH,
                            it.getBlob(it.getColumnIndexOrThrow(Keys.Columns.ENCRYPTED_KEY))
                        )
                    )
                } else {
                    throw Exception("No master key")
                }
            }
        }

    fun addCategory(entry: DecryptableCategoryEntry) =
        readableDatabase.query(
            true,
            Category,
            setOf(Category.Columns.CAT_ID, Category.Columns.NAME),
            whereEq(Category.Columns.NAME, entry.encryptedName)
        ).use {
            if (it.count > 0) {
                // TODO: THIS MAKES NO SENSE! Add shouldn't succeed, if something exists...
                it.moveToFirst()
                it.getDBID(Category.Columns.CAT_ID)
            } else { // there isn't already such a category...
                this.writableDatabase.insert(Category,
                    ContentValues().apply {
                        put(Category.Columns.NAME, entry.encryptedName)
                    }
                )
            }
        }

    fun deleteCategory(id: DBID) =
        writableDatabase.delete(
            Category,
            whereEq(Category.Columns.CAT_ID, id)
        )

    fun fetchAllCategoryRows(categoriesFlow: MutableStateFlow<List<DecryptableCategoryEntry>>? = null): List<DecryptableCategoryEntry> =
        readableDatabase.let { db ->
            db.query(
                Category,
                setOf(Category.Columns.CAT_ID, Category.Columns.NAME)
            ).use {
                ArrayList<DecryptableCategoryEntry>().apply {
                    it.moveToFirst()
                    (0 until it.count).forEach { _ ->
                        val category = DecryptableCategoryEntry().apply {
                            id = it.getDBID(Category.Columns.CAT_ID)
                            encryptedName = it.getIVCipher(Category.Columns.NAME)
                        }
                        add(category)
                        if (categoriesFlow != null)
                            categoriesFlow.value += category
                        it.moveToNext()
                    }
                }.toList()
            }
        }

//    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//    // TODO: DELETE, no one uses!
//    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//    fun getCategoryCount(id: DBID) =
//        readableDatabase.let { db ->
//            db.rawQuery(
//                "SELECT count(*) FROM ${SiteEntry.tableName} WHERE category=$id",
//                null
//            ).use {
//                if (it.count > 0) {
//                    it.moveToFirst()
//                    it.getInt(0)
//                } else 0
//            }
//        }

    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry) =
        writableDatabase.update(Category, ContentValues().apply {
            put(Category.Columns.NAME, entry.encryptedName)
        }, whereEq(Category.Columns.CAT_ID, id)).toLong()

    fun fetchAllRows(
        categoryId: DBID? = null,
        softDeletedOnly: Boolean = false,
        siteEntriesFlow: MutableStateFlow<List<DecryptableSiteEntry>>? = null
    ) = readableDatabase.let { db ->
        db.query(
            SiteEntry,
            SiteEntry.Columns.entries.toSet(),
            if (categoryId != null) {
                whereEq(SiteEntry.Columns.CATEGORY_ID, categoryId)
            } else if (softDeletedOnly) {
                whereNot(SiteEntry.Columns.DELETED, 0)
            } else null,
        ).use {
            it.moveToFirst()
            ArrayList<DecryptableSiteEntry>().apply {
                (0 until it.count).forEach { _ ->
                    // TODO: Until we get chainable selects..filter here
                    if (softDeletedOnly || it.getInt(it.getColumnIndexOrThrow(SiteEntry.Columns.DELETED)) == 0) {
                        val siteEntry =
                            DecryptableSiteEntry(it.getDBID(SiteEntry.Columns.CATEGORY_ID)).apply {
                                id = it.getDBID(SiteEntry.Columns.SITEENTRY_ID)
                                password = it.getIVCipher(SiteEntry.Columns.PASSWORD)
                                description = it.getIVCipher(SiteEntry.Columns.DESCRIPTION)
                                username = it.getIVCipher(SiteEntry.Columns.USERNAME)
                                website = it.getIVCipher(SiteEntry.Columns.WEBSITE)
                                note = it.getIVCipher(SiteEntry.Columns.NOTE)
                                photo = it.getIVCipher(SiteEntry.Columns.PHOTO)
                                deleted = it.getDBID(SiteEntry.Columns.DELETED)
                                extensions = it.getIVCipher(SiteEntry.Columns.EXTENSIONS)
                                try {
                                    it.getZonedDateTimeOfPasswordChange()
                                        ?.let { time -> passwordChangedDate = time }
                                } catch (ex: Exception) {
                                    firebaseRecordException("Date parsing issue", ex)
                                }
                            }
                        add(siteEntry)
                        if (siteEntriesFlow != null)
                            siteEntriesFlow.value += siteEntry
                    }
                    it.moveToNext()
                }
            }.toList()
        }
    }

    fun updateSiteEntry(entry: DecryptableSiteEntry): DBID {
        require(entry.id != null) { "Cannot update SiteEntry without ID" }
        require(entry.categoryId != null) { "Cannot update SiteEntry without Category ID" }
        val args = ContentValues().apply {
            put(SiteEntry.Columns.DESCRIPTION, entry.description)
            put(SiteEntry.Columns.USERNAME, entry.username)
            put(SiteEntry.Columns.PASSWORD, entry.password)
            put(SiteEntry.Columns.WEBSITE, entry.website)
            put(SiteEntry.Columns.NOTE, entry.note)
            put(SiteEntry.Columns.PHOTO, entry.photo)
            if (entry.passwordChangedDate != null) {
                put(
                    SiteEntry.Columns.PASSWORD_CHANGE_DATE,
                    entry.passwordChangedDate!!
                )
            }
            put(SiteEntry.Columns.EXTENSIONS, entry.extensions)
        }
        val ret = writableDatabase.update(
            SiteEntry,
            args,
            whereEq(SiteEntry.Columns.SITEENTRY_ID, entry.id!!)
        )
        assert(ret == 1) { "Oh no...DB update failed to update..." }
        return entry.id as DBID
    }

    fun updateSiteEntryCategory(id: DBID, newCategoryId: DBID) =
        writableDatabase.update(
            SiteEntry,
            ContentValues().apply {
                put(SiteEntry.Columns.CATEGORY_ID, newCategoryId)
            }, whereEq(SiteEntry.Columns.SITEENTRY_ID, id)
        )

    fun addSiteEntry(entry: DecryptableSiteEntry) =
        writableDatabase.insertOrThrow(SiteEntry, ContentValues().apply {
            if (entry.id != null) {
                put(SiteEntry.Columns.SITEENTRY_ID, entry.id)
            }
            put(SiteEntry.Columns.CATEGORY_ID, entry.categoryId)
            put(SiteEntry.Columns.PASSWORD, entry.password)
            put(SiteEntry.Columns.DESCRIPTION, entry.description)
            put(SiteEntry.Columns.USERNAME, entry.username)
            put(SiteEntry.Columns.WEBSITE, entry.website)
            put(SiteEntry.Columns.NOTE, entry.note)
            put(SiteEntry.Columns.PHOTO, entry.photo)
            put(SiteEntry.Columns.DELETED, entry.deleted)
            entry.passwordChangedDate?.let {
                put(SiteEntry.Columns.PASSWORD_CHANGE_DATE, it)
            }
            put(SiteEntry.Columns.EXTENSIONS, entry.extensions)
        })

    fun restoreSoftDeletedSiteEntry(id: DBID) =
        writableDatabase.update(
            SiteEntry,
            ContentValues().apply {
                put(SiteEntry.Columns.DELETED, 0)
            },
            whereEq(SiteEntry.Columns.SITEENTRY_ID, id)
        )

    fun markSiteEntryDeleted(id: DBID) =
        writableDatabase.update(
            SiteEntry,
            ContentValues().apply {
                put(SiteEntry.Columns.DELETED, System.currentTimeMillis())
            },
            whereEq(SiteEntry.Columns.SITEENTRY_ID, id)
        )

    fun hardDeleteSiteEntry(id: DBID) =
        writableDatabase.delete(
            SiteEntry,
            whereEq(SiteEntry.Columns.SITEENTRY_ID, id)
        )

    // Begin restoration, starts a transaction, if preparation fails, exception is throw and changes have been rolled back
    fun beginRestoration(): SQLiteDatabase =
        writableDatabase.apply {
            beginTransaction()
            // best effort to rid of all the tables
            try {
                listOf(
                    SiteEntry,
                    Category,
                    GooglePasswordManager,
                    SiteEntry2GooglePasswordManager
                ).forEach { tables ->
                    tables.drop().forEach { sql ->
                        execSQL(sql)
                    }
                    tables.create().forEach { sql ->
                        execSQL(sql)
                    }
                }
            } catch (ex: Exception) {
                endTransaction()
                throw ex
            }
        }

    // if user imports new DB , encryption changes and
    // we don't currently convert GPMs too..all data in the table is irrevocably lost
    fun deleteAllSavedGPMs() = writableDatabase.let { db ->
        db.execSQL("DELETE FROM ${GooglePasswordManager.tableName};")
        db.execSQL("DELETE FROM ${SiteEntry2GooglePasswordManager.tableName};")
    }

    fun markSavedGPMIgnored(savedGPMID: DBID) =
        writableDatabase.let { db ->
            db.update(GooglePasswordManager, ContentValues().apply {
                put(GooglePasswordManager.Columns.STATUS, 1)
            }, whereEq(GooglePasswordManager.Columns.ID, savedGPMID)).toLong()
        }

    fun linkSaveGPMAndSiteEntry(siteEntryID: DBID, savedGPMID: DBID): SQLiteDatabase =
        writableDatabase.apply {
            insert(SiteEntry2GooglePasswordManager, ContentValues().apply {
                put(SiteEntry2GooglePasswordManager.Columns.PASSWORD_ID, siteEntryID)
                put(SiteEntry2GooglePasswordManager.Columns.GOOGLE_ID, savedGPMID)
            })
        }


    fun fetchAllSiteEntryGPMMappings(): Map<DBID, Set<DBID>> =
        readableDatabase.let { db ->
            db.query(
                SiteEntry2GooglePasswordManager,
                SiteEntry2GooglePasswordManager.Columns.entries.toSet(),
            ).use { c ->
                (0 until c.count)
                    .map { _ ->
                        c.moveToNext()
                        c.getDBID(SiteEntry2GooglePasswordManager.Columns.PASSWORD_ID) to
                                c.getDBID(SiteEntry2GooglePasswordManager.Columns.GOOGLE_ID)
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, values) -> values.toSet() }
            }
        }


    fun fetchSavedGPMsFromDB(
        where: SelectionCondition? = null,
        gpmsFlow: MutableStateFlow<Set<SavedGPM>>? = null
    ): Set<SavedGPM> =
        readableDatabase.let { db ->
            db.query(
                GooglePasswordManager,
                GooglePasswordManager.Columns.entries.toSet(),
                where
            ).use {
                it.moveToFirst()
                ArrayList<SavedGPM>().apply {
                    (0 until it.count).forEach { _ ->
                        val gpm = makeFromEncryptedStringFields(
                            it.getDBID(GooglePasswordManager.Columns.ID),
                            it.getIVCipher(GooglePasswordManager.Columns.NAME),
                            it.getIVCipher(GooglePasswordManager.Columns.URL),
                            it.getIVCipher(GooglePasswordManager.Columns.USERNAME),
                            it.getIVCipher(GooglePasswordManager.Columns.PASSWORD),
                            it.getIVCipher(GooglePasswordManager.Columns.NOTE),
                            it.getDBID(GooglePasswordManager.Columns.STATUS) == 1L,
                            it.getString(GooglePasswordManager.Columns.HASH),
                        )
                        add(gpm)
                        if (gpmsFlow != null)
                            gpmsFlow.value += gpm
                        it.moveToNext()
                    }
                }.toSet()
            }
        }

    fun deleteObsoleteSavedGPMs(delete: Set<SavedGPM>) =
        delete.forEach { savedGPM ->
            writableDatabase.delete(
                GooglePasswordManager,
                whereEq(GooglePasswordManager.Columns.ID, savedGPM.id!!)
            )
        }

    fun updateSavedGPMByIncomingGPM(update: Map<IncomingGPM, SavedGPM>) =
        update.forEach { (incomingGPM, savedGPM) ->
            this.writableDatabase.update(
                GooglePasswordManager,
                ContentValues().apply {
                    put(GooglePasswordManager.Columns.NAME, incomingGPM.name.encrypt())
                    put(GooglePasswordManager.Columns.URL, incomingGPM.url.encrypt())
                    put(GooglePasswordManager.Columns.USERNAME, incomingGPM.username.encrypt())
                    put(GooglePasswordManager.Columns.PASSWORD, incomingGPM.password.encrypt())
                    put(GooglePasswordManager.Columns.NOTE, incomingGPM.note.encrypt())
                    put(GooglePasswordManager.Columns.HASH, incomingGPM.hash)
                },
                whereEq(GooglePasswordManager.Columns.ID, savedGPM.id!!)
            )
        }

    fun addNewIncomingGPM(add: Set<IncomingGPM>) =
        add.forEach { incomingGPM ->
            this.writableDatabase.insert(GooglePasswordManager,
                ContentValues().apply {
                    put(GooglePasswordManager.Columns.ID, null) // auto increment
                    put(GooglePasswordManager.Columns.NAME, incomingGPM.name.encrypt())
                    put(GooglePasswordManager.Columns.URL, incomingGPM.url.encrypt())
                    put(GooglePasswordManager.Columns.USERNAME, incomingGPM.username.encrypt())
                    put(GooglePasswordManager.Columns.PASSWORD, incomingGPM.password.encrypt())
                    put(GooglePasswordManager.Columns.NOTE, incomingGPM.note.encrypt())
                    put(GooglePasswordManager.Columns.STATUS, 0)
                    put(GooglePasswordManager.Columns.HASH, incomingGPM.hash)
                }
            )
        }

    fun addSavedGPM(savedGPM: SavedGPM) =
        this.writableDatabase.insert(GooglePasswordManager,
            ContentValues().apply {
                put(GooglePasswordManager.Columns.ID, savedGPM.id) // auto increment
                put(GooglePasswordManager.Columns.NAME, savedGPM.encryptedName)
                put(GooglePasswordManager.Columns.URL, savedGPM.encryptedUrl)
                put(GooglePasswordManager.Columns.USERNAME, savedGPM.encryptedUsername)
                put(GooglePasswordManager.Columns.PASSWORD, savedGPM.encryptedPassword)
                put(GooglePasswordManager.Columns.NOTE, savedGPM.encryptedNote)
                put(GooglePasswordManager.Columns.STATUS, if (savedGPM.flaggedIgnored) 1 else 0)
                put(GooglePasswordManager.Columns.HASH, savedGPM.hash)
            }
        )

    companion object {
        private const val DATABASE_VERSION = 7
        const val DATABASE_NAME = "safe"
        private const val TAG = "DBHelper"

        // oh the ... DROP COLUMN not supported until 3.50.0 and above
        private fun sqliteVersion(): String = try {
            SQLiteDatabase.create(null).use {
                DatabaseUtils.stringForQuery(it, "SELECT sqlite_version()", null)
            }
        } catch (ex: Exception) {
            "0"
        }

        private fun compareSqliteVersions(version1: String, version2: String): Int {
            val parts1 = version1.split(".")
            val parts2 = version2.split(".")
            val maxLength = maxOf(parts1.size, parts2.size)

            for (i in 0 until maxLength) {
                val num1 = parts1.getOrElse(i) { "0" }.toInt()
                val num2 = parts2.getOrElse(i) { "0" }.toInt()

                if (num1 != num2) {
                    return num1.compareTo(num2)
                }
            }
            return 0 // versions are equal
        }

        fun upgradeFromV6ToV7AddExtensionsColumn(db: SQLiteDatabase, upgrade: Int) {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE ${SiteEntry.tableName} ADD COLUMN extensions TEXT")
            } catch (ex: SQLiteException) {
                Log.i(TAG, "onUpgrade $upgrade: $ex")
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }

        fun upgradeFromV5ToV6AddDeletedColumn(db: SQLiteDatabase, upgrade: Int) {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE ${SiteEntry.tableName} ADD COLUMN deleted INTEGER DEFAULT 0")
            } catch (ex: SQLiteException) {
                Log.i(TAG, "onUpgrade $upgrade: $ex")
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }

        fun upgradeFromV4ToV5MergeKeys(db: SQLiteDatabase, upgrade: Int) {
            db.beginTransaction()
            try {
                GooglePasswordManager.create().forEach {
                    db.execSQL(it)
                }
                SiteEntry2GooglePasswordManager.create().forEach {
                    db.execSQL(it)
                }
            } catch (ex: SQLiteException) {
                Log.i(TAG, "onUpgrade $upgrade: $ex")
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }

        fun upgradeFromV3ToV4MergeKeys(db: SQLiteDatabase, upgrade: Int) {
            db.beginTransaction()
            var masterKey: IVCipherText? = null
            var salt: Salt? = null
            try {
                db.query(
                    true,
                    "master_key",
                    arrayOf("encryptedkey"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ).use {
                    if (it.count > 0) {
                        it.moveToFirst()
                        masterKey = IVCipherText(
                            CipherUtilities.IV_LENGTH,
                            it.getBlob(it.getColumnIndexOrThrow("encryptedkey"))
                        )
                    }
                }

                db.query(
                    true, "salt", arrayOf("salt"), null,
                    null,
                    null,
                    null,
                    null,
                    null
                ).use { c ->
                    if (c.count > 0) {
                        c.moveToFirst()
                        val dsalt = c.getBlob(c.getColumnIndexOrThrow("salt"))
                        salt = Salt(dsalt)
                    }
                }
            } catch (ex: SQLiteException) {
                // possibly we've already done the upgrade, so just skip the transfer
                Log.i(TAG, "onUpgrade $upgrade: $ex")
            }

            db.execSQL("DROP TABLE IF EXISTS master_key;")
            db.execSQL("DROP TABLE IF EXISTS salt;")
            Keys.create().forEach { sql ->
                db.execSQL(sql)
            }

            if (masterKey != null && salt != null) {
                db.insert(
                    Keys,
                    ContentValues().apply {
                        put(Keys.Columns.ENCRYPTED_KEY, masterKey!!)
                        put(Keys.Columns.SALT, salt!!.salt)
                    }
                )
            } else {
                // should never happen obviously, perhaps user installed OLD version, never logged in..
                Log.w(TAG, "Failed migrating masterkey ${sqliteVersion()}")
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }

        fun upgradeFromV2ToV3RemoveLastDateTimeEdit(db: SQLiteDatabase, upgrade: Int) {
            if (compareSqliteVersions(sqliteVersion(), "3.55.5") >= 0) {
                db.beginTransaction()
                try {
                    db.execSQL("ALTER TABLE categories DROP COLUMN lastdatetimeedit;")
                } catch (ex: SQLiteException) {
                    Log.i(TAG, "onUpgrade $upgrade: $ex")
                }
                db.setTransactionSuccessful()
                db.endTransaction()
            } else {
                // do it the hard way...
                db.beginTransaction()
                db.execSQL("CREATE TABLE new_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL);")
                db.execSQL("INSERT INTO new_categories(id, name) SELECT id, name FROM categories;")
                db.execSQL("DROP TABLE categories;")
                db.execSQL("ALTER TABLE new_categories RENAME TO categories;")
                db.setTransactionSuccessful()
                db.endTransaction()
            }
        }

        fun upgradeFromV1ToV2AddPhoto(db: SQLiteDatabase, upgrade: Int) {
            db.beginTransaction()
            try {
                db.execSQL(
                    "ALTER TABLE ${SiteEntry.tableName} ADD COLUMN photo TEXT;",
                )
            } catch (ex: SQLiteException) {
                if (ex.message?.contains("duplicate column") != true) {
                    // something else wrong than duplicate column
                    Log.i(TAG, "onUpgrade $upgrade: $ex")
                    throw ex
                }
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }
    }
}
