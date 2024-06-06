package fi.iki.ede.safe.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.date.DateUtils
import fi.iki.ede.crypto.keystore.CipherUtilities
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
        listOf(Category, Password, Keys).forEach {
            try {
                db?.execSQL(it.create())
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
            beginTransaction()
            // DONT USE Use{} transaction will die

            delete(Keys).let {
                insert(
                    Keys,
                    ContentValues().apply {
                        put(Keys.KeysColumns.ENCRYPTED_KEY, ivCipher)
                        put(Keys.KeysColumns.SALT, salt.salt)
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
            db.query(true, Keys, setOf(Keys.KeysColumns.ENCRYPTED_KEY, Keys.KeysColumns.SALT)).use {
                if (it.count > 0) {
                    it.moveToFirst()
                    val salt = it.getBlob(it.getColumnIndexOrThrow(Keys.KeysColumns.SALT))
                    Pair(
                        Salt(salt),
                        IVCipherText(
                            CipherUtilities.IV_LENGTH,
                            it.getBlob(it.getColumnIndexOrThrow(Keys.KeysColumns.ENCRYPTED_KEY))
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
            setOf(Category.CategoryColumns.CAT_ID, Category.CategoryColumns.NAME),
            whereEq(Category.CategoryColumns.NAME, entry.encryptedName)
        ).use {
            if (it.count > 0) {
                // TODO: THIS MAKES NO SENSE! Add shouldn't succeed, if something exists...
                it.moveToFirst()
                it.getDBID(Category.CategoryColumns.CAT_ID)
            } else { // there isn't already such a category...
                this.writableDatabase.insert(Category,
                    ContentValues().apply {
                        put(Category.CategoryColumns.NAME, entry.encryptedName)
                    }
                )
            }
        }

    fun deleteCategory(id: DBID) =
        writableDatabase.use { db ->
            db.delete(
                Category,
                whereEq(Category.CategoryColumns.CAT_ID, id)
            )
        }

    fun fetchAllCategoryRows(): List<DecryptableCategoryEntry> =
        readableDatabase.use { db ->
            db.query(
                Category,
                setOf(Category.CategoryColumns.CAT_ID, Category.CategoryColumns.NAME)
            ).use {
                ArrayList<DecryptableCategoryEntry>().apply {
                    it.moveToFirst()
                    (0 until it.count).forEach { _ ->
                        add(DecryptableCategoryEntry().apply {
                            id = it.getDBID(Category.CategoryColumns.CAT_ID)
                            encryptedName = it.getIVCipher(Category.CategoryColumns.NAME)
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
                put(Category.CategoryColumns.NAME, entry.encryptedName)
            }, whereEq(Category.CategoryColumns.CAT_ID, id)).toLong()
        }

    fun fetchAllRows(categoryId: DBID? = null) =
        readableDatabase.use { db ->
            db.query(
                Password,
                Password.PasswordColumns.values().toSet(),
                if (categoryId != null) {
                    whereEq(Password.PasswordColumns.CATEGORY_ID, categoryId)
                } else null,
            ).use {
                it.moveToFirst()
                ArrayList<DecryptableSiteEntry>().apply {
                    (0 until it.count).forEach { _ ->
                        add(DecryptableSiteEntry(it.getDBID(Password.PasswordColumns.CATEGORY_ID)).apply {
                            id = it.getDBID(Password.PasswordColumns.PWD_ID)
                            password = it.getIVCipher(Password.PasswordColumns.PASSWORD)
                            description = it.getIVCipher(Password.PasswordColumns.DESCRIPTION)
                            username = it.getIVCipher(Password.PasswordColumns.USERNAME)
                            website = it.getIVCipher(Password.PasswordColumns.WEBSITE)
                            note = it.getIVCipher(Password.PasswordColumns.NOTE)
                            photo = it.getIVCipher(Password.PasswordColumns.PHOTO)
                            it.getZonedDateTimeOfPasswordChange()
                                ?.let { passwordChangedDate = it }
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
            put(Password.PasswordColumns.DESCRIPTION, entry.description)
            put(Password.PasswordColumns.USERNAME, entry.username)
            put(Password.PasswordColumns.PASSWORD, entry.password)
            put(Password.PasswordColumns.WEBSITE, entry.website)
            put(Password.PasswordColumns.NOTE, entry.note)
            put(Password.PasswordColumns.PHOTO, entry.photo)
            if (entry.passwordChangedDate != null) {
                put(
                    Password.PasswordColumns.PASSWORD_CHANGE_DATE,
                    entry.passwordChangedDate!!
                )
            }
        }
        val ret = writableDatabase.use { db ->
            db.update(
                Password,
                args,
                whereEq(Password.PasswordColumns.PWD_ID, entry.id!!)
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
                    put(Password.PasswordColumns.CATEGORY_ID, newCategoryId)
                }, whereEq(Password.PasswordColumns.PWD_ID, id)
            )
        }

    fun addPassword(entry: DecryptableSiteEntry) =
        // DONT USE Use{} transaction will die
        writableDatabase.insertOrThrow(Password, ContentValues().apply {
            if (entry.id != null) {
                put(Password.PasswordColumns.PWD_ID, entry.id)
            }
            put(Password.PasswordColumns.CATEGORY_ID, entry.categoryId)
            put(Password.PasswordColumns.PASSWORD, entry.password)
            put(Password.PasswordColumns.DESCRIPTION, entry.description)
            put(Password.PasswordColumns.USERNAME, entry.username)
            put(Password.PasswordColumns.WEBSITE, entry.website)
            put(Password.PasswordColumns.NOTE, entry.note)
            put(Password.PasswordColumns.PHOTO, entry.photo)
            entry.passwordChangedDate?.let {
                put(Password.PasswordColumns.PASSWORD_CHANGE_DATE, it)
            }
        })

    fun deletePassword(id: DBID) =
        writableDatabase.use { db ->
            db.delete(
                Password,
                whereEq(Password.PasswordColumns.PWD_ID, id)
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
                ).forEach { sql ->
                    execSQL(sql.drop())
                    execSQL(sql.create())
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
            db.execSQL(Keys.create())

            if (masterKey != null && salt != null) {
                db.insert(
                    Keys,
                    ContentValues().apply {
                        put(Keys.KeysColumns.ENCRYPTED_KEY, masterKey!!)
                        put(Keys.KeysColumns.SALT, salt!!.salt)
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

interface Table {
    val tableName: String
    fun create(): String
    fun drop(): String
}

interface TableColumns<T : Table> {
    val columnName: String
}

private object Password : Table {
    override val tableName: String
        get() = "passwords"

    enum class PasswordColumns(override val columnName: String) : TableColumns<Password> {
        PWD_ID("id"),
        CATEGORY_ID("category"),
        PASSWORD("password"),
        DESCRIPTION("description"),
        USERNAME("username"),
        WEBSITE("website"),
        NOTE("note"),
        PHOTO("photo"),
        PASSWORD_CHANGE_DATE("passwordchangeddate")
    }

    override fun create() = """
CREATE TABLE IF NOT EXISTS $tableName (
    ${PasswordColumns.PWD_ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${PasswordColumns.CATEGORY_ID.columnName} INTEGER NOT NULL,
    ${PasswordColumns.PASSWORD.columnName} TEXT NOT NULL,
    ${PasswordColumns.DESCRIPTION.columnName} TEXT NOT NULL,
    ${PasswordColumns.USERNAME.columnName} TEXT,
    ${PasswordColumns.WEBSITE.columnName} TEXT,
    ${PasswordColumns.NOTE.columnName} TEXT,
    ${PasswordColumns.PHOTO.columnName} TEXT,
    ${PasswordColumns.PASSWORD_CHANGE_DATE.columnName} TEXT);
"""

    override fun drop() = "DROP TABLE IF EXISTS $tableName;"
}

private object Category : Table {
    override val tableName: String
        get() = "categories"

    override fun create() = """
CREATE TABLE IF NOT EXISTS $tableName (
    ${CategoryColumns.CAT_ID.columnName} INTEGER PRIMARY KEY AUTOINCREMENT,
    ${CategoryColumns.NAME.columnName} TEXT NOT NULL);
        """

    override fun drop() = "DROP TABLE IF EXISTS $tableName;"

    enum class CategoryColumns(override val columnName: String) : TableColumns<Category> {
        CAT_ID("id"),
        NAME("name"),
    }
}

private object Keys : Table {
    override val tableName: String
        get() = "keys"

    // if you EVER alter this, copy this as hardcoded string to onUpgrade above
    override fun create() = """
CREATE TABLE IF NOT EXISTS $tableName (
    ${KeysColumns.ENCRYPTED_KEY.columnName} TEXT NOT NULL,
    ${KeysColumns.SALT.columnName} TEXT NOT NULL
    );
        """

    override fun drop() = "DROP TABLE IF EXISTS ${tableName};"

    enum class KeysColumns(override val columnName: String) : TableColumns<Keys> {
        ENCRYPTED_KEY("encryptedkey"),
        SALT("salt"),
    }
}

private fun ContentValues.put(column: TableColumns<*>, value: IVCipherText) =
    put(column.columnName, value.combineIVAndCipherText())

private fun ContentValues.put(column: TableColumns<*>, value: Long?) =
    put(column.columnName, value)

private fun ContentValues.put(column: TableColumns<*>, value: ByteArray) =
    put(column.columnName, value)

private fun ContentValues.put(column: TableColumns<*>, date: ZonedDateTime) =
    put(column, DateUtils.toUnixSeconds(date))

private fun Cursor.getColumnIndexOrThrow(column: TableColumns<*>) =
    getColumnIndexOrThrow(column.columnName)

private fun Cursor.getZonedDateTimeOfPasswordChange(): ZonedDateTime? =
    getString(getColumnIndexOrThrow(Password.PasswordColumns.PASSWORD_CHANGE_DATE))?.let { date ->
        date.toLongOrNull()?.let {
            DateUtils.unixEpochSecondsToLocalZonedDateTime(it)
        } ?: run {
            //ok, we have something that isn't numerical
            DateUtils.newParse(date)
        }
    }

private fun Cursor.getIVCipher(column: TableColumns<*>) =
    IVCipherText(
        CipherUtilities.IV_LENGTH,
        getBlob(getColumnIndexOrThrow(column.columnName)) ?: byteArrayOf(),
    )

private fun Cursor.getDBID(column: TableColumns<*>) =
    getLong(getColumnIndexOrThrow(column.columnName))

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.update(
    table: T,
    values: ContentValues,
    selection: SelectionCondition? = null
) = update(table.tableName, values, selection?.query(), selection?.args())

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    distinct: Boolean,
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(
    distinct, table.tableName, columns.map { it.columnName }.toTypedArray(),
    selection?.query(), selection?.args(), null, null, null, null
)

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.query(
    table: T,
    columns: Set<C>,
    selection: SelectionCondition? = null // TODO: THIS
) = query(false, table, columns, selection)

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.delete(
    table: T,
    selection: SelectionCondition? = null
) = delete(table.tableName, selection?.query(), selection?.args())

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insert(
    table: T,
    values: ContentValues
) =
    insert(table.tableName, null, values)

private fun <T : Table, C : TableColumns<T>> SQLiteDatabase.insertOrThrow(
    table: T,
    values: ContentValues
) =
    insert(table.tableName, null, values)

class SelectionCondition(
    private val column: TableColumns<*>,
    private val singleArg: Any,
    private val comparison: String = "="
) {
    fun query() = "(${column.columnName} $comparison ?)"
    fun args() = arrayOf(singleArg.toString())

    companion object {

        fun alwaysMatch() = SelectionCondition(
            column = object : TableColumns<Nothing> {
                override val columnName = "1"
            },
            singleArg = "1",
            comparison = "="
        )
    }
}

private fun <T : Table, C : TableColumns<T>> whereEq(
    column: TableColumns<T>,
    whereArg: Any
) = SelectionCondition(column, whereArg, "=")
