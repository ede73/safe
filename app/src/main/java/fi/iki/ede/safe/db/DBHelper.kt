package fi.iki.ede.safe.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.safe.model.Preferences
import java.time.ZonedDateTime

typealias DBID = Long

/**
 * Construct new DBHelper
 *
 * Mockk has has bugs in constructor mocking (results in android verifier rejecting the class)
 * So as a work around, DBHelperFactory will provide the DBHelper (or DBHelper mock) in test
 */
object DBHelperFactory {
    fun getDBHelper(context: Context) = DBHelper(context)
}

class DBHelper internal constructor(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION,
    // Alas API 33:OpenParams.Builder().setJournalMode(JOURNAL_MODE_MEMORY).build()
) {

    override fun onCreate(db: SQLiteDatabase?) {
        // pragma user_version
        // this will be 0 on first run (even if OLD database DOES exist)
        val version = db?.version

        listOf(CATEGORIES_CREATE, PASSWORDS_CREATE, MASTER_KEY_CREATE, SALT_CREATE).forEach {
            try {
                db?.execSQL(it)
            } catch (ex: SQLiteException) {
                Log.e(TAG, "error", ex)
                // if upgrading from OLD DB BEFORE sqlite open helper
                // the version is NOT set(ie.0) (but the tables might exist since previous installation)
                // we'll swallow the exception
                if (version != 0) {
                    // however if there ALREADY is a versioning AND
                    // table somehow exists, this is an error
                    throw ex
                }
            }
        }
    }

    // called once (regardless of amount of upgrades needed)
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                if (db != null) {
                    db.beginTransaction()
                    db.execSQL(
                        "ALTER TABLE $TABLE_PASSWORDS ADD COLUMN $COL_PASSWORDS_PHOTO TEXT;",
                    )
                    db.setTransactionSuccessful()
                    db.endTransaction()
                }
            }

            else -> throw IllegalStateException(
                "onUpgrade() with unknown oldVersion $oldVersion"
            )
        }
    }

    // called once (regardless of amount of downgrades needed)
    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Downgrade $oldVersion to $newVersion")
    }

    private fun fetchSalt() =
        readableDatabase.use { db ->
            db.query(
                true, TABLE_SALT, arrayOf("salt"),
                null, null, null, null, null, null
            ).use { c ->
                if (c.count > 0) {
                    c.moveToFirst()
                    val salt = c.getBlob(c.getColumnIndexOrThrow("salt"))
                    Salt(salt)
                } else {
                    throw SQLException("Could not read SALT")
                }
            }
        }

    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) {
        // DONT USE Use{} transaction will die
        storeSalt(salt)
        // DONT USE Use{} transaction will die
        storeEncryptedMasterKey(ivCipher)
        Preferences.setMasterkeyInitialized()
    }

    // TODO: Replace with SaltedEncryptedPassword (once it supports IVCipher)
    fun fetchSaltAndEncryptedMasterKey() =
        Pair(fetchSalt(), fetchMasterKey())


    private fun storeSalt(salt: Salt) =
        // DONT USE Use{} transaction will die
        writableDatabase.apply {
            delete(TABLE_SALT, "1=1", null).let {
                insert(
                    TABLE_SALT,
                    null,
                    ContentValues().apply { put("salt", salt.salt) })

            }
        }

    private fun fetchMasterKey() =
        readableDatabase.use { db ->
            db.query(
                true, TABLE_MASTER_KEY, arrayOf("encryptedkey"),
                null, null, null, null, null, null
            ).use {
                if (it.count > 0) {
                    it.moveToFirst()
                    IVCipherText(
                        KeyStoreHelper.IV_LENGTH,
                        it.getBlob(it.getColumnIndexOrThrow("encryptedkey"))
                    )
                } else {
                    throw Exception("No master key")
                }
            }
        }

    private fun storeEncryptedMasterKey(ivCipher: IVCipherText) =
        writableDatabase.delete(TABLE_MASTER_KEY, "1=1", null).let {
            writableDatabase.insert(
                TABLE_MASTER_KEY,
                null,
                ContentValues().apply { put("encryptedkey", ivCipher) }
            )
        }


    fun addCategory(entry: DecryptableCategoryEntry) =
        // DONT USE Use{} transaction will die
        readableDatabase.query(
            true, TABLE_CATEGORIES, arrayOf(
                "id", "name"
            ), "name='${entry.encryptedName}'", null, null, null, null, null
        ).use {
            if (it.count > 0) {
                it.moveToFirst()
                it.getDBID("id")
            } else { // there isn't already such a category...
                this.writableDatabase.insert(TABLE_CATEGORIES, null,
                    ContentValues().apply {
                        put("name", entry.encryptedName)
                    }
                )
            }
        }

    fun deleteCategory(id: DBID) =
        writableDatabase.use { db -> db.delete(TABLE_CATEGORIES, "id=$id", null) }

    fun fetchAllCategoryRows(): List<DecryptableCategoryEntry> =
        readableDatabase.use { db ->
            db.query(
                TABLE_CATEGORIES, arrayOf(
                    "id", "name"
                ),
                null, null, null, null, null
            ).use {
                ArrayList<DecryptableCategoryEntry>().apply {
                    it.moveToFirst()
                    (0 until it.count).forEach { _ ->
                        add(DecryptableCategoryEntry().apply {
                            id = it.getDBID("id")
                            encryptedName = it.getIVCipher("name")
                        })
                        it.moveToNext()
                    }
                }
            }
        }

    private fun Cursor.getZonedDateTimeOfPasswordChange(): ZonedDateTime? =
        getString(getColumnIndexOrThrow(COL_PASSWORDS_PASSWORD_CHANGED_DATE))?.let { date ->
            date.toLongOrNull()?.let {
                DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
            } ?: run {
                //ok, we have something that isn't numerical
                DateUtils.newParse(date)
            }
        }

    private fun Cursor.getIVCipher(columnName: String) =
        IVCipherText(
            getBlob(getColumnIndexOrThrow(columnName)) ?: byteArrayOf(),
            KeyStoreHelper.IV_LENGTH
        )

    private fun Cursor.getDBID(columnName: String) =
        getLong(getColumnIndexOrThrow(columnName))

    fun getCategoryCount(id: DBID) =
        readableDatabase.use { db ->
            db.rawQuery(
                "SELECT count(*) FROM $TABLE_PASSWORDS WHERE category=$id",
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
            db.update(TABLE_CATEGORIES, ContentValues().apply {
                put("name", entry.encryptedName)
            }, "id=$id", null).toLong()
        }

    fun fetchAllRows(categoryId: DBID? = null) =
        readableDatabase.use { db ->
            db.query(
                TABLE_PASSWORDS, arrayOf(
                    COL_PASSWORDS_ID,
                    COL_PASSWORDS_PASSWORD,
                    COL_PASSWORDS_DESCRIPTION,
                    COL_PASSWORDS_USERNAME,
                    COL_PASSWORDS_WEBSITE,
                    COL_PASSWORDS_NOTE,
                    COL_PASSWORDS_CATEGORY,
                    COL_PASSWORDS_PASSWORD_CHANGED_DATE,
                    COL_PASSWORDS_PHOTO
                ), if (categoryId != null) {
                    "$COL_PASSWORDS_CATEGORY=$categoryId"
                } else null, null, null, null, null
            ).use {
                it.moveToFirst()
                ArrayList<DecryptableSiteEntry>().apply {
                    (0 until it.count).forEach { _ ->
                        add(DecryptableSiteEntry(it.getDBID(COL_PASSWORDS_CATEGORY)).apply {
                            id = it.getDBID(COL_PASSWORDS_ID)
                            password = it.getIVCipher(COL_PASSWORDS_PASSWORD)
                            description = it.getIVCipher(COL_PASSWORDS_DESCRIPTION)
                            username = it.getIVCipher(COL_PASSWORDS_USERNAME)
                            website = it.getIVCipher(COL_PASSWORDS_WEBSITE)
                            note = it.getIVCipher(COL_PASSWORDS_NOTE)
                            photo = it.getIVCipher(COL_PASSWORDS_PHOTO)
                            it.getZonedDateTimeOfPasswordChange()
                                ?.let { passwordChangedDate = it }
                        })
                        it.moveToNext()
                    }
                }
            }
        }


    fun updatePassword(entry: DecryptableSiteEntry): DBID {
        require(entry.id != null) { "Cannot update password without ID" }
        require(entry.categoryId != null) { "Cannot update password without Category ID" }
        val args = ContentValues().apply {
            put(COL_PASSWORDS_DESCRIPTION, entry.description)
            put(COL_PASSWORDS_USERNAME, entry.username)
            put(COL_PASSWORDS_PASSWORD, entry.password)
            put(COL_PASSWORDS_WEBSITE, entry.website)
            put(COL_PASSWORDS_NOTE, entry.note)
            put(COL_PASSWORDS_PHOTO, entry.photo)
            if (entry.passwordChangedDate != null) {
                put(
                    COL_PASSWORDS_PASSWORD_CHANGED_DATE,
                    entry.passwordChangedDate!!
                )
            }
        }
        val ret = writableDatabase.use { db ->
            db.update(
                TABLE_PASSWORDS,
                args,
                "$COL_PASSWORDS_ID=${entry.id}",
                null
            )
        }
        assert(ret == 1) { "Oh no...DB update failed to update..." }
        return entry.id as DBID
    }

    fun updatePasswordCategory(id: DBID, newCategoryId: DBID) =
        writableDatabase.use { db ->
            db.update(
                TABLE_PASSWORDS,
                ContentValues().apply {
                    put("category", newCategoryId)
                }, "id=$id", null
            )
        }

    private fun ContentValues.put(key: String, value: IVCipherText) =
        put(key, value.combineIVAndCipherText())


    private fun ContentValues.put(key: String, date: ZonedDateTime) =
        put(key, DateUtils.toUnixSeconds(date))

    fun addPassword(entry: DecryptableSiteEntry) =
        // DONT USE Use{} transaction will die
        writableDatabase.insertOrThrow(TABLE_PASSWORDS, null, ContentValues().apply {
            if (entry.id != null) {
                put(COL_PASSWORDS_ID, entry.id)
            }
            put(COL_PASSWORDS_CATEGORY, entry.categoryId)
            put(COL_PASSWORDS_PASSWORD, entry.password)
            put(COL_PASSWORDS_DESCRIPTION, entry.description)
            put(COL_PASSWORDS_USERNAME, entry.username)
            put(COL_PASSWORDS_WEBSITE, entry.website)
            put(COL_PASSWORDS_NOTE, entry.note)
            put(COL_PASSWORDS_PHOTO, entry.photo)
            entry.passwordChangedDate?.let {
                put(COL_PASSWORDS_PASSWORD_CHANGED_DATE, it)
            }
        })

    fun deletePassword(id: DBID) =
        writableDatabase.use { db ->
            db.delete(
                TABLE_PASSWORDS,
                "$COL_PASSWORDS_ID=$id",
                null
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
                    PASSWORDS_DROP,
                    PASSWORDS_CREATE,
                    CATEGORIES_DROP,
                    CATEGORIES_CREATE,
                ).forEach { sql ->
                    execSQL(sql)
                }
            } catch (ex: Exception) {
                endTransaction()
                throw ex
            }
        }


    companion object {
        private const val TABLE_PASSWORDS = "passwords"
        private const val TABLE_CATEGORIES = "categories"
        private const val TABLE_MASTER_KEY = "master_key"
        private const val TABLE_SALT = "salt"
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "safe"
        private const val TAG = "DBHelper"

        // DO NOT EDIT, if you do, you have to create a migration plan for the DB
        private const val COL_PASSWORDS_ID = "id"
        private const val COL_PASSWORDS_CATEGORY = "category"
        private const val COL_PASSWORDS_PASSWORD = "password"
        private const val COL_PASSWORDS_DESCRIPTION = "description"
        private const val COL_PASSWORDS_USERNAME = "username"
        private const val COL_PASSWORDS_WEBSITE = "website"
        private const val COL_PASSWORDS_NOTE = "note"
        private const val COL_PASSWORDS_PHOTO = "photo"

        private const val COL_PASSWORDS_PASSWORD_CHANGED_DATE = "passwordchangeddate"
        private const val PASSWORDS_CREATE = """CREATE TABLE $TABLE_PASSWORDS (
                $COL_PASSWORDS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PASSWORDS_CATEGORY INTEGER NOT NULL,
                $COL_PASSWORDS_PASSWORD TEXT NOT NULL,
                $COL_PASSWORDS_DESCRIPTION TEXT NOT NULL,
                $COL_PASSWORDS_USERNAME TEXT,
                $COL_PASSWORDS_WEBSITE TEXT,
                $COL_PASSWORDS_NOTE TEXT,
                $COL_PASSWORDS_PHOTO TEXT,
                $COL_PASSWORDS_PASSWORD_CHANGED_DATE TEXT);""" // TODO: Could turn changed date to INTEGER?
        private const val PASSWORDS_DROP = "DROP TABLE $TABLE_PASSWORDS;"
        private const val CATEGORIES_CREATE = """CREATE TABLE $TABLE_CATEGORIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                lastdatetimeedit TEXT);"""
        private const val CATEGORIES_DROP = "DROP TABLE $TABLE_CATEGORIES;"
        private const val MASTER_KEY_CREATE = """CREATE TABLE $TABLE_MASTER_KEY (
                encryptedkey TEXT NOT NULL);"""
        private const val SALT_CREATE = "CREATE TABLE $TABLE_SALT (salt TEXT NOT NULL);"
    }
}