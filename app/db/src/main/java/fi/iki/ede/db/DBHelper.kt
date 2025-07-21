package fi.iki.ede.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseRecordException
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZonedDateTime
import kotlin.reflect.KFunction0

typealias DBID = Long

/**
 * Bug!? in SQLite? Do not use readableDatabase or writeableDatabase.use!
 * It will close the instrumentation test in-memory database unconditionally.
 * Even if you hold the database reference, you specifically increase reference
 * count by acquiring reference, you open a transaction and dont close it (yet).
 * What ever I tried, the WHOLE database gets close. And is with in-memory DBs
 * once closed, all data is lost :)
 */
class DBHelper(
    context: Context,
    databaseName: String? = DATABASE_NAME,
    regularAppNotATest: Boolean = false,
    getExternalTables: KFunction0<List<Table>>,
    private val upgradeExternalTables: (db: SQLiteDatabase, upgrade: Int) -> Unit = { _, _ -> },
) : SQLiteOpenHelper(
    context, databaseName, null, DATABASE_VERSION,
    // Alas API 33:OpenParams.Builder().setJournalMode(JOURNAL_MODE_MEMORY).build()
) {
    private val dynamicTables = mutableListOf<Table>()

    init {
        if (!regularAppNotATest || System.getProperty("test") == "test") {
            // MUST be a test case
            require(databaseName == null) { "MUST BE InMemory DB" }
        } else {
            require(databaseName != null) { "Cannot use InMemory DB in prod" }
            require(System.getProperty("test") != "test") { "Test cannot use file DB" }
        }
        dynamicTables.addAll(getExternalTables())
    }

//    @OptIn(ExperimentalStdlibApi::class)
//    fun dumpInMemoryDb() {
//        return
//        val database = readableDatabase
//        val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
//        while (cursor.moveToNext()) {
//            val tableName = cursor.getString(0)
//            Logger.d(TAG,"Table: $tableName")
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
//                Logger.d(TAG,)
//            }
//            tableCursor.close()
//        }
//        cursor.close()
//    }

    private var onCreateCalled = false
    private fun getTables() = getTablesForRestoration() + listOf(Keys)
    private fun getTablesForRestoration() = listOf(
        Category,
        SiteEntry,
    ) + dynamicTables

    override fun onCreate(db: SQLiteDatabase?) {
        require(!onCreateCalled) { "ON create called TWICE" }
        onCreateCalled = true
        getTables().forEach {
            try {
                it.create().forEach { sql ->
                    db?.execSQL(sql)
                }
            } catch (ex: SQLiteException) {
                Logger.e(TAG, "Error initializing database, sqliteVersion=${sqliteVersion()}", ex)
                throw ex
            }
        }
    }

    // called once (regardless of amount of upgrades needed)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // DO USE hardcoded strings for DB references, as it records history
        (oldVersion until newVersion).forEach { upgrade ->
            Logger.i(TAG, "onUpgrade $upgrade (until $newVersion), sqlite ${sqliteVersion()}")
            when (upgrade) {
                0 -> {
                    // should never happen except maybe during upgrade test scenarios
                }

                1 -> upgradeFromV1ToV2AddPhoto(db) {
                    upgradeExternalTables(it, upgrade)
                }

                2 -> {
                    // there's no drop column support prior to 3.50.0 - no harm leaving the column
                    // compared to alternative - full table recreation and copy
                    // Actually its buggy (potentially destructive) until 3.35.5
                    upgradeFromV2ToV3RemoveLastDateTimeEdit(db) {
                        upgradeExternalTables(it, upgrade)
                    }
                }

                3 -> {
                    upgradeFromV3ToV4MergeKeys(db) {
                        upgradeExternalTables(it, upgrade)
                    }
                }

                4 -> {
                    upgradeFromV4ToV5MergeKeys(db) {
                        upgradeExternalTables(it, upgrade)
                    }
                }

                5 -> {
                    upgradeFromV5ToV6AddDeletedColumn(db) {
                        upgradeExternalTables(it, upgrade)
                    }
                }

                6 -> {
                    upgradeFromV6ToV7AddExtensionsColumn(db) {
                        upgradeExternalTables(it, upgrade)
                    }
                }

                else -> Logger.w(
                    TAG, "onUpgrade() with unknown oldVersion $oldVersion to $newVersion"
                )
            }
        }
    }

    // called once (regardless of amount of downgrades needed)
    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Logger.i(TAG, "Downgrade $oldVersion to $newVersion")
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
                this.writableDatabase.insert(
                    Category,
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

    fun fetchPhotoOnly(siteEntryID: DBID): IVCipherText? = readableDatabase.let { db ->
        db.query(
            SiteEntry,
            SiteEntry.lazyColumns(),
            whereEq(SiteEntry.Columns.SITEENTRY_ID, siteEntryID)
        ).use {
            if (it.moveToFirst()) {
                IVCipherText(
                    CipherUtilities.IV_LENGTH,
                    it.getBlob(it.getColumnIndexOrThrow(SiteEntry.Columns.PHOTO))
                        ?: byteArrayOf()
                )
            } else null
        }
    }

    fun fetchAllRows(
        categoryId: DBID? = null,
        softDeletedOnly: Boolean = false,
        siteEntriesFlow: MutableStateFlow<List<DecryptableSiteEntry>>? = null
    ) = readableDatabase.let { db ->
        db.query(
            SiteEntry,
            SiteEntry.Columns.entries.toMutableSet().apply {
                removeAll(SiteEntry.lazyColumns())
            }.toSet(),
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
                            DecryptableSiteEntry(it.getDBID(SiteEntry.Columns.CATEGORY_ID))
                                .apply {
                                    id = it.getDBID(SiteEntry.Columns.SITEENTRY_ID)
                                    password = it.getIVCipher(SiteEntry.Columns.PASSWORD)
                                    description = it.getIVCipher(SiteEntry.Columns.DESCRIPTION)
                                    username = it.getIVCipher(SiteEntry.Columns.USERNAME)
                                    website = it.getIVCipher(SiteEntry.Columns.WEBSITE)
                                    note = it.getIVCipher(SiteEntry.Columns.NOTE)
                                    it.getIVCipher(
                                        SiteEntry.Columns.PHOTO
                                    ) { loadedPhoto ->
                                        photo = loadedPhoto
                                    }
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
        return entry.id!!
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
                getTablesForRestoration().forEach { tables ->
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

        @Suppress("SameParameterValue", "yes, for now version2 is always 3.55.5")
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

        fun upgradeFromV6ToV7AddExtensionsColumn(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            db.transaction {
                try {
                    execSQL("ALTER TABLE ${SiteEntry.tableName} ADD COLUMN extensions TEXT")
                } catch (ex: SQLiteException) {
                    Logger.i(TAG, "onUpgrade to 7: $ex")
                }
                upgradeExternals(this)
            }
        }

        fun upgradeFromV5ToV6AddDeletedColumn(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            db.transaction {
                try {
                    execSQL("ALTER TABLE ${SiteEntry.tableName} ADD COLUMN deleted INTEGER DEFAULT 0")
                } catch (ex: SQLiteException) {
                    Logger.i(TAG, "onUpgrade to 6: $ex")
                }
                upgradeExternals(this)
            }
        }

        fun upgradeFromV4ToV5MergeKeys(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            db.transaction {
                upgradeExternals(this)
            }
        }

        fun upgradeFromV3ToV4MergeKeys(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            db.transaction {
                var masterKey: IVCipherText? = null
                var salt: Salt? = null
                try {
                    query(
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

                    query(
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
                    upgradeExternals(this)
                } catch (ex: SQLiteException) {
                    // possibly we've already done the upgrade, so just skip the transfer
                    Logger.i(TAG, "onUpgrade to 4: $ex")
                }

                execSQL("DROP TABLE IF EXISTS master_key;")
                execSQL("DROP TABLE IF EXISTS salt;")
                Keys.create().forEach { sql ->
                    execSQL(sql)
                }

                if (masterKey != null && salt != null) {
                    insert(
                        Keys,
                        ContentValues().apply {
                            put(Keys.Columns.ENCRYPTED_KEY, masterKey!!)
                            put(Keys.Columns.SALT, salt!!.salt)
                        }
                    )
                } else {
                    // should never happen obviously, perhaps user installed OLD version, never logged in..
                    Logger.w(TAG, "Failed migrating masterkey ${sqliteVersion()}")
                }
            }
        }

        fun upgradeFromV2ToV3RemoveLastDateTimeEdit(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            if (compareSqliteVersions(sqliteVersion(), "3.55.5") >= 0) {
                db.transaction {
                    try {
                        execSQL("ALTER TABLE categories DROP COLUMN lastdatetimeedit;")
                    } catch (ex: SQLiteException) {
                        Logger.i(TAG, "onUpgrade to 3: $ex")
                    }
                    upgradeExternals(this)
                }
            } else {
                // do it the hard way...
                db.transaction {
                    execSQL("CREATE TABLE new_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL);")
                    execSQL("INSERT INTO new_categories(id, name) SELECT id, name FROM categories;")
                    execSQL("DROP TABLE categories;")
                    execSQL("ALTER TABLE new_categories RENAME TO categories;")
                }
            }
        }

        fun upgradeFromV1ToV2AddPhoto(
            db: SQLiteDatabase,
            upgradeExternals: (db: SQLiteDatabase) -> Unit
        ) {
            db.transaction {
                try {
                    execSQL(
                        "ALTER TABLE ${SiteEntry.tableName} ADD COLUMN photo TEXT;",
                    )
                    upgradeExternals(this)
                } catch (ex: SQLiteException) {
                    if (ex.message?.contains("duplicate column") != true) {
                        // something else wrong than duplicate column
                        Logger.i(TAG, "onUpgrade to 2: $ex")
                        throw ex
                    }
                }
            }
        }
    }
}

fun Cursor.getZonedDateTimeOfPasswordChange(): ZonedDateTime? =
    getString(getColumnIndexOrThrow(SiteEntry.Columns.PASSWORD_CHANGE_DATE))?.let { date ->
        date.toLongOrNull()?.let {
            DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
        } ?: run {
            //ok, we have something that isn't numerical
            DateUtils.newParse(date)
        }
    }