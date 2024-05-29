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
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.KeyStoreHelper
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
        val tables =
            listOf(CATEGORIES_CREATE, PASSWORDS_CREATE, MASTER_KEY_CREATE, SALT_CREATE)
        // pragma user_version
        // this will be 0 on first run (even if OLD database DOES exist)
        val version = db?.version

        for (table in tables) {
            try {
                db?.execSQL(table)
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

    fun isUninitializedDatabase(): Boolean = try {
        val f = fetchSalt()
        f.isEmpty()
    } catch (ex: SQLException) {
        true
    }


    private fun fetchSalt(): Salt {
        readableDatabase.use { db ->
            db.query(
                true, TABLE_SALT, arrayOf("salt"),
                null, null, null, null, null, null
            ).use { c ->
                if (c.count > 0) {
                    c.moveToFirst()
                    val salt = c.getBlob(c.getColumnIndexOrThrow("salt"))
                    return Salt(salt)
                }
                throw SQLException("Could not read SALT")
            }
        }
    }

    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) {
        storeSalt(salt)
        storeEncryptedMasterKey(ivCipher)
    }

    // TODO: Replace with SaltedEncryptedPassword (once it supports IVCipher)
    fun fetchSaltAndEncryptedMasterKey(): Pair<Salt, IVCipherText> {
        return Pair(fetchSalt(), fetchMasterKey())
    }

    private fun storeSalt(salt: Salt) {
        this.writableDatabase.delete(TABLE_SALT, "1=1", null)
        val args = ContentValues()
        args.put("salt", salt.salt)
        this.writableDatabase.insert(TABLE_SALT, null, args)
    }

    private fun fetchMasterKey(): IVCipherText {
        val c = this.readableDatabase.query(
            true, TABLE_MASTER_KEY, arrayOf("encryptedkey"),
            null, null, null, null, null, null
        )
        if (c.count > 0) {
            c.moveToFirst()
            val key = c.getBlob(c.getColumnIndexOrThrow("encryptedkey"))
            c.close()
            return IVCipherText(KeyStoreHelper.IV_LENGTH, key)
        }
        c.close()
        throw Exception("No master key")
    }

    private fun storeEncryptedMasterKey(ivCipher: IVCipherText) {
        this.writableDatabase.delete(TABLE_MASTER_KEY, "1=1", null)
        val args = ContentValues()
        args.put("encryptedkey", ivCipher)
        this.writableDatabase.insert(TABLE_MASTER_KEY, null, args)
    }

    fun addCategory(entry: DecryptableCategoryEntry): DBID {
        val c = this.readableDatabase.query(
            true, TABLE_CATEGORIES, arrayOf(
                "id", "name"
            ), "name='" + entry.encryptedName + "'", null, null, null, null, null
        )
        val rowID: DBID =
            if (c.count > 0) {
                c.moveToFirst()
                c.getDBID("id")
            } else { // there isn't already such a category...
                val initialValues = ContentValues()
                initialValues.put("name", entry.encryptedName)
                this.writableDatabase.insert(TABLE_CATEGORIES, null, initialValues)
            }
        c.close()
        return rowID
    }

    fun deleteCategory(id: DBID) {
        this.writableDatabase.delete(TABLE_CATEGORIES, "id=$id", null)
    }

    fun fetchAllCategoryRows(): List<DecryptableCategoryEntry> {
        val ret = ArrayList<DecryptableCategoryEntry>()
        val c = this.readableDatabase.query(
            TABLE_CATEGORIES, arrayOf(
                "id", "name"
            ),
            null, null, null, null, null
        )
        val numRows = c.count
        c.moveToFirst()
        for (i in 0 until numRows) {
            val row = DecryptableCategoryEntry()
            row.id = c.getDBID("id")
            row.encryptedName = c.getIVCipher("name")
            ret.add(row)
            c.moveToNext()
        }
        c.close()
        return ret
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

    private fun Cursor.getIVCipher(columnName: String): IVCipherText {
        val blob = getBlob(getColumnIndexOrThrow(columnName)) ?: byteArrayOf()
        return IVCipherText(blob, KeyStoreHelper.IV_LENGTH)
    }

    private fun Cursor.getDBID(columnName: String): DBID {
        return getLong(getColumnIndexOrThrow(columnName))
    }

    fun getCategoryCount(id: DBID): Int {
        val c = this.readableDatabase.rawQuery(
            "SELECT count(*) FROM $TABLE_PASSWORDS WHERE category=$id",
            null
        )
        val count: Int = if (c.count > 0) {
            c.moveToFirst()
            c.getInt(0)
        } else 0
        c.close()
        return count
    }

    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry) {
        val args = ContentValues()
        args.put("name", entry.encryptedName)
        this.writableDatabase.update(TABLE_CATEGORIES, args, "id=$id", null)
    }

    fun fetchAllRows(categoryId: DBID? = null): List<DecryptablePasswordEntry> {
        val ret = ArrayList<DecryptablePasswordEntry>()
        val rows = arrayOf(
            COL_PASSWORDS_ID,
            COL_PASSWORDS_PASSWORD,
            COL_PASSWORDS_DESCRIPTION,
            COL_PASSWORDS_USERNAME,
            COL_PASSWORDS_WEBSITE,
            COL_PASSWORDS_NOTE,
            COL_PASSWORDS_CATEGORY,
            COL_PASSWORDS_PASSWORD_CHANGED_DATE,
            COL_PASSWORDS_PHOTO
        )
        val c: Cursor = this.readableDatabase.query(
            TABLE_PASSWORDS, rows, if (categoryId != null) {
                "$COL_PASSWORDS_CATEGORY=$categoryId"
            } else null, null, null, null, null
        )
        val numRows = c.count
        c.moveToFirst()
        for (i in 0 until numRows) {
            val passwordRow = DecryptablePasswordEntry(c.getDBID(COL_PASSWORDS_CATEGORY))
            passwordRow.id = c.getDBID(COL_PASSWORDS_ID)
            passwordRow.password = c.getIVCipher(COL_PASSWORDS_PASSWORD)
            passwordRow.description = c.getIVCipher(COL_PASSWORDS_DESCRIPTION)
            passwordRow.username = c.getIVCipher(COL_PASSWORDS_USERNAME)
            passwordRow.website = c.getIVCipher(COL_PASSWORDS_WEBSITE)
            passwordRow.note = c.getIVCipher(COL_PASSWORDS_NOTE)
            passwordRow.photo = c.getIVCipher(COL_PASSWORDS_PHOTO)
            c.getZonedDateTimeOfPasswordChange()?.let { passwordRow.passwordChangedDate = it }
            ret.add(passwordRow)
            c.moveToNext()
        }
        c.close()
        return ret
    }

    fun updatePassword(entry: DecryptablePasswordEntry): DBID {
        require(entry.id != null) { "Cannot update password without ID" }
        require(entry.categoryId != null) { "Cannot update password without Category ID" }
        val args = ContentValues()
        args.put(COL_PASSWORDS_DESCRIPTION, entry.description)
        args.put(COL_PASSWORDS_USERNAME, entry.username)
        args.put(COL_PASSWORDS_PASSWORD, entry.password)
        args.put(COL_PASSWORDS_WEBSITE, entry.website)
        args.put(COL_PASSWORDS_NOTE, entry.note)
        args.put(COL_PASSWORDS_PHOTO, entry.photo)
        if (entry.passwordChangedDate != null) {
            args.put(
                COL_PASSWORDS_PASSWORD_CHANGED_DATE,
                entry.passwordChangedDate!!
            )
        }
        val ret = this.writableDatabase.update(
            TABLE_PASSWORDS,
            args,
            "$COL_PASSWORDS_ID=${entry.id}",
            null
        )
        assert(ret == 1) { "Oh no...DB update failed to update..." }
        return entry.id as DBID
    }

    fun updatePasswordCategory(id: DBID, newCategoryId: DBID) {
        val args = ContentValues()
        args.put("category", newCategoryId)
        this.writableDatabase.update(TABLE_PASSWORDS, args, "id=$id", null)
    }

    private fun ContentValues.put(key: String, value: IVCipherText) {
        put(key, value.combineIVAndCipherText())
    }

    private fun ContentValues.put(key: String, date: ZonedDateTime) {
        put(key, DateUtils.toUnixSeconds(date))
    }

    fun addPassword(entry: DecryptablePasswordEntry): DBID {
        val initialValues = ContentValues()
        if (entry.id != null) {
            initialValues.put(COL_PASSWORDS_ID, entry.id)
        }
        initialValues.put(COL_PASSWORDS_CATEGORY, entry.categoryId)
        initialValues.put(COL_PASSWORDS_PASSWORD, entry.password)
        initialValues.put(COL_PASSWORDS_DESCRIPTION, entry.description)
        initialValues.put(COL_PASSWORDS_USERNAME, entry.username)
        initialValues.put(COL_PASSWORDS_WEBSITE, entry.website)
        initialValues.put(COL_PASSWORDS_NOTE, entry.note)
        initialValues.put(COL_PASSWORDS_PHOTO, entry.photo)
        entry.passwordChangedDate?.let {
            initialValues.put(COL_PASSWORDS_PASSWORD_CHANGED_DATE, it)
        }
        return this.writableDatabase.insertOrThrow(TABLE_PASSWORDS, null, initialValues)
    }

    fun deletePassword(id: DBID) {
        this.writableDatabase.delete(TABLE_PASSWORDS, "$COL_PASSWORDS_ID=$id", null)
    }

    fun beginRestoration(): SQLiteDatabase {
        val db = this.writableDatabase
        db.beginTransaction()
        // best effort to rid of all the tables
        listOf(
            PASSWORDS_DROP,
            PASSWORDS_CREATE,
            CATEGORIES_DROP,
            CATEGORIES_CREATE,
        ).forEach {
            try {
                db.execSQL(it)
            } catch (e: SQLException) {
                Log.e(TAG, "SQLite exception: " + e.localizedMessage)
            }
        }
        return db
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