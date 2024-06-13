package fi.iki.ede.safe.db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.safe.model.Preferences

typealias DBID = Long

class DBHelper internal constructor(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION,
    // Alas API 33:OpenParams.Builder().setJournalMode(JOURNAL_MODE_MEMORY).build()
) {
    override fun onCreate(db: SQLiteDatabase?) {
        listOf(Category, Password, Keys).forEach {
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
            // DONT USE Use{} transaction will die

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
            Preferences.setMasterkeyInitialized()
        }
    }

    // TODO: Replace with SaltedEncryptedPassword (once it supports IVCipher)
    fun fetchSaltAndEncryptedMasterKey() =
        readableDatabase.use { db ->
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
    // DONT USE Use{} transaction will die
        //            "${Category.CategoryColumns.NAME.columnName}='${entry.encryptedName}'",
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
        writableDatabase.use { db ->
            db.delete(
                Category,
                whereEq(Category.Columns.CAT_ID, id)
            )
        }

    fun fetchAllCategoryRows(): List<DecryptableCategoryEntry> =
        readableDatabase.use { db ->
            db.query(
                Category,
                setOf(Category.Columns.CAT_ID, Category.Columns.NAME)
            ).use {
                ArrayList<DecryptableCategoryEntry>().apply {
                    it.moveToFirst()
                    (0 until it.count).forEach { _ ->
                        add(DecryptableCategoryEntry().apply {
                            id = it.getDBID(Category.Columns.CAT_ID)
                            encryptedName = it.getIVCipher(Category.Columns.NAME)
                        })
                        it.moveToNext()
                    }
                }.toList()
            }
        }

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // TODO: DELETE, no one uses!
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    fun getCategoryCount(id: DBID) =
        readableDatabase.use { db ->
            db.rawQuery(
                "SELECT count(*) FROM ${Password.tableName} WHERE category=$id",
                null
            ).use {
                if (it.count > 0) {
                    it.moveToFirst()
                    it.getInt(0)
                } else 0
            }
        }

    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry) =
        writableDatabase.use { db ->
            db.update(Category, ContentValues().apply {
                put(Category.Columns.NAME, entry.encryptedName)
            }, whereEq(Category.Columns.CAT_ID, id)).toLong()
        }

    fun fetchAllRows(categoryId: DBID? = null) =
        readableDatabase.use { db ->
            db.query(
                Password,
                Password.Columns.values().toSet(),
                if (categoryId != null) {
                    whereEq(Password.Columns.CATEGORY_ID, categoryId)
                } else null,
            ).use {
                it.moveToFirst()
                ArrayList<DecryptableSiteEntry>().apply {
                    (0 until it.count).forEach { _ ->
                        add(DecryptableSiteEntry(it.getDBID(Password.Columns.CATEGORY_ID)).apply {
                            id = it.getDBID(Password.Columns.PWD_ID)
                            password = it.getIVCipher(Password.Columns.PASSWORD)
                            description = it.getIVCipher(Password.Columns.DESCRIPTION)
                            username = it.getIVCipher(Password.Columns.USERNAME)
                            website = it.getIVCipher(Password.Columns.WEBSITE)
                            note = it.getIVCipher(Password.Columns.NOTE)
                            photo = it.getIVCipher(Password.Columns.PHOTO)
                            try {
                                it.getZonedDateTimeOfPasswordChange()
                                    ?.let { passwordChangedDate = it }
                            } catch (ex: Exception) {
                                Log.d(TAG, "Date parsing issue", ex)
                            }
                        })
                        it.moveToNext()
                    }
                }.toList()
            }
        }


    fun updatePassword(entry: DecryptableSiteEntry): DBID {
        require(entry.id != null) { "Cannot update password without ID" }
        require(entry.categoryId != null) { "Cannot update password without Category ID" }
        val args = ContentValues().apply {
            put(Password.Columns.DESCRIPTION, entry.description)
            put(Password.Columns.USERNAME, entry.username)
            put(Password.Columns.PASSWORD, entry.password)
            put(Password.Columns.WEBSITE, entry.website)
            put(Password.Columns.NOTE, entry.note)
            put(Password.Columns.PHOTO, entry.photo)
            if (entry.passwordChangedDate != null) {
                put(
                    Password.Columns.PASSWORD_CHANGE_DATE,
                    entry.passwordChangedDate!!
                )
            }
        }
        val ret = writableDatabase.use { db ->
            db.update(
                Password,
                args,
                whereEq(Password.Columns.PWD_ID, entry.id!!)
            )
        }
        assert(ret == 1) { "Oh no...DB update failed to update..." }
        return entry.id as DBID
    }

    fun updatePasswordCategory(id: DBID, newCategoryId: DBID) =
        writableDatabase.use { db ->
            db.update(
                Password,
                ContentValues().apply {
                    put(Password.Columns.CATEGORY_ID, newCategoryId)
                }, whereEq(Password.Columns.PWD_ID, id)
            )
        }

    fun addPassword(entry: DecryptableSiteEntry) =
        // DONT USE Use{} transaction will die
        writableDatabase.insertOrThrow(Password, ContentValues().apply {
            if (entry.id != null) {
                put(Password.Columns.PWD_ID, entry.id)
            }
            put(Password.Columns.CATEGORY_ID, entry.categoryId)
            put(Password.Columns.PASSWORD, entry.password)
            put(Password.Columns.DESCRIPTION, entry.description)
            put(Password.Columns.USERNAME, entry.username)
            put(Password.Columns.WEBSITE, entry.website)
            put(Password.Columns.NOTE, entry.note)
            put(Password.Columns.PHOTO, entry.photo)
            entry.passwordChangedDate?.let {
                put(Password.Columns.PASSWORD_CHANGE_DATE, it)
            }
        })

    fun deletePassword(id: DBID) =
        writableDatabase.use { db ->
            db.delete(
                Password,
                whereEq(Password.Columns.PWD_ID, id)
            )
        }

    // Begin restoration, starts a transaction, if preparation fails, exception is throw and changes have been rolled back
    fun beginRestoration(): SQLiteDatabase =
        // DONT USE Use{} transaction will die
        writableDatabase.apply {
            beginTransaction()
            // best effort to rid of all the tables
            try {
                listOf(
                    Password,
                    Category,
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

    companion object {
        private const val DATABASE_VERSION = 4
        private const val DATABASE_NAME = "safe"
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
                    "ALTER TABLE passwords ADD COLUMN photo TEXT;",
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
