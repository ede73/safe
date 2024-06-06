package fi.iki.ede.safe

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.safe.db.DBHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DBUpgradeTests {
    private lateinit var dbHelper: InMemorySQLiteOpenHelper
    private lateinit var context: Context
    private val fakeSalt = "abcdabcd01234567"
    private val fakeMasterkey = "00112233445566778899AABBCCDDEEFF99887766554433221100123456789ABC"
    private fun basePasswordTable(additions: String = "") = """
                CREATE TABLE passwords (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category INTEGER NOT NULL,
                    password TEXT NOT NULL,
                    description TEXT NOT NULL,
                    username TEXT,
                    website TEXT,
                    note TEXT,
                    $additions
                    passwordchangeddate TEXT);"""

    private fun baseCategoriesTable(additions: String = "") = """
                CREATE TABLE categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                    ${additions});"""

    private val oldKeys = listOf(
        "CREATE TABLE master_key (encryptedkey TEXT NOT NULL);",
        "CREATE TABLE salt (salt TEXT NOT NULL);",
        "INSERT INTO master_key VALUES (X'$fakeMasterkey');",
        "INSERT INTO salt VALUES (X'$fakeSalt');"
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun absoluteV1() = InMemorySQLiteOpenHelper(context, 1) {
        // Set the database as it appeared long ago in version 1
        listOf(
            basePasswordTable(),
            baseCategoriesTable(",lastdatetimeedit TEXT"),
        ) + oldKeys
    }

    private fun absoluteV2() = InMemorySQLiteOpenHelper(context, 2) {
        // Set the database as it appeared long ago in version 3
        // ie. passwords has photo
        listOf(
            basePasswordTable("photo TEXT,"),
            baseCategoriesTable(",lastdatetimeedit TEXT"),
        ) + oldKeys
    }

    private fun absoluteV3() = InMemorySQLiteOpenHelper(context, 3) {
        // Set the database as it appeared long ago in version 2
        // ie. drop lastedit from category
        listOf(
            basePasswordTable("photo TEXT,"),
            baseCategoriesTable(),
        ) + oldKeys
    }

    private fun absoluteV4() = InMemorySQLiteOpenHelper(context, 4) {
        // Set the database as it appeared long ago in version 4
        // ie. passwords has photo,no last edit, merged keys
        listOf(
            basePasswordTable("photo TEXT,"),
            baseCategoriesTable(),
            "CREATE TABLE keys (encryptedkey TEXT NOT NULL, salt TEXT NOT NULL);",
            "INSERT INTO (encryptedkey, salt) VALUES (X'$fakeMasterkey', X'$fakeSalt');"
        )
    }

    @Test
    fun testUpgradeV1ToV2() {
        dbHelper = absoluteV1()
        val database = dbHelper.writableDatabase.verify(1)
        dbHelper.onUpgrade(database, 1, 2)
        database.verify(2)
        database.close()
    }

    @Test
    fun testUpgradeV1ToV3() {
        dbHelper = absoluteV1()
        val database = dbHelper.writableDatabase.verify(1)
        dbHelper.onUpgrade(database, 1, 3)
        database.verify(3)
        database.close()
    }

    @Test
    fun testUpgradeV1ToV4() {
        dbHelper = absoluteV1()
        val database = dbHelper.writableDatabase.verify(1)
        dbHelper.onUpgrade(database, 1, 4)
        database.verify(4)
        database.close()
    }

    @Test
    fun testUpgradeV2ToV3() {
        // just fool proof for NOT somehow messing up V2 upgrade, we start from what V2 should be
        dbHelper = absoluteV2()
        val database = dbHelper.writableDatabase.verify(2)
        dbHelper.onUpgrade(database, 2, 3)
        database.verify(3).close()
    }

    @Test
    fun testUpgradeV2ToV4() {
        dbHelper = absoluteV2()
        val database = dbHelper.writableDatabase.verify(2)
        dbHelper.onUpgrade(database, 2, 4)
        database.verify(4).close()
    }

    @Test
    fun testUpgradeV3ToV4() {
        Log.i("xx", baseCategoriesTable(""))
        dbHelper = absoluteV3()
        val database = dbHelper.writableDatabase.verify(3)
        dbHelper.onUpgrade(database, 3, 4)
        database.verify(4).close()
    }

    private fun tableExists(database: SQLiteDatabase, tableName: String): Boolean =
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'",
            null
        ).use { cursor ->
            return cursor.count > 0
        }


    private fun columnExists(
        database: SQLiteDatabase,
        tableName: String,
        columnName: String
    ): Boolean {
        var columnExists = false
        database.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndex("name"))
                if (name == columnName) {
                    columnExists = true
                    break
                }
            }
        }
        return columnExists
    }

    private fun getAllColumnValues(
        database: SQLiteDatabase,
        tableName: String,
        columnName: String
    ): List<String> {
        val valuesList = mutableListOf<String>()
        val query = "SELECT $columnName, typeof($columnName) as type FROM $tableName"
        database.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val type = cursor.getString(cursor.getColumnIndex("type"))
                    when (type) {
                        "blob" -> {
                            val blob = cursor.getBlob(cursor.getColumnIndex(columnName))
                            valuesList.add(blob.toHexString())
                        }

                        else -> {
                            valuesList.add(cursor.getString(cursor.getColumnIndex(columnName)))
                        }
                    }
                } while (cursor.moveToNext())
            }
        }
        return valuesList
    }

    private fun SQLiteDatabase.verify(currentVersion: Int): SQLiteDatabase {
        when (currentVersion) {
            1 -> {
                // original version
                assert(tableExists(this, "categories"))
                assert(tableExists(this, "passwords"))
                assert(tableExists(this, "master_key"))
                assert(tableExists(this, "salt"))
                assert(columnExists(this, "categories", "lastdatetimeedit"))
                assert(!columnExists(this, "passwords", "photo"))

                val salts = getAllColumnValues(this, "salt", "salt")
                val masterKeys = getAllColumnValues(this, "master_key", "encryptedkey")
                assert(masterKeys.size == 1)
                assert(salts.size == 1)
                assert(masterKeys[0].lowercase().contentEquals(fakeMasterkey.lowercase()))
                assert(salts[0].lowercase().contentEquals(fakeSalt.lowercase()))
            }

            2 -> {
                // added photo to passwords
                assert(tableExists(this, "categories"))
                assert(tableExists(this, "passwords"))
                assert(tableExists(this, "master_key"))
                assert(tableExists(this, "salt"))
                assert(columnExists(this, "passwords", "photo"))

                val masterKeys = getAllColumnValues(this, "master_key", "encryptedkey")
                val salts = getAllColumnValues(this, "salt", "salt")
                assert(masterKeys.size == 1)
                assert(salts.size == 1)
                assert(masterKeys[0].lowercase().contentEquals(fakeMasterkey.lowercase()))
                assert(salts[0].lowercase().contentEquals(fakeSalt.lowercase()))
            }

            3 -> {
                // delete useless lastdatetimeedit
                assert(tableExists(this, "categories"))
                assert(tableExists(this, "passwords"))
                assert(tableExists(this, "master_key"))
                assert(tableExists(this, "salt"))
                assert(!columnExists(this, "categories", "lastdatetimeedit"))
                assert(columnExists(this, "passwords", "photo"))

                val masterKeys = getAllColumnValues(this, "master_key", "encryptedkey")
                val salts = getAllColumnValues(this, "salt", "salt")
                assert(masterKeys.size == 1)
                assert(salts.size == 1)
                assert(masterKeys[0].lowercase().contentEquals(fakeMasterkey.lowercase()))
                assert(salts[0].lowercase().contentEquals(fakeSalt.lowercase()))
            }

            4 -> {
                // converge keys
                assert(tableExists(this, "categories"))
                assert(tableExists(this, "passwords"))
                assert(tableExists(this, "keys"))
                assert(!tableExists(this, "master_key"))
                assert(!tableExists(this, "salt"))

                val masterKeys = getAllColumnValues(this, "keys", "encryptedkey")
                val salts = getAllColumnValues(this, "keys", "salt")
                assert(masterKeys.size == 1)
                assert(salts.size == 1)
                assert(masterKeys[0].lowercase().contentEquals(fakeMasterkey.lowercase()))
                assert(salts[0].lowercase().contentEquals(fakeSalt.lowercase()))
            }
        }
        return this
    }
}

fun dumpDatabaseSchema(database: SQLiteDatabase): String {
    val schema = StringBuilder()
    val query = "SELECT sql FROM sqlite_master"
    database.rawQuery(query, null).use { cursor ->
        if (cursor.moveToFirst()) {
            do {
                schema.append(cursor.getString(cursor.getColumnIndex("sql"))).append(";\n")
            } while (cursor.moveToNext())
        }
    }
    return schema.toString()
}

class InMemorySQLiteOpenHelper(
    context: Context,
    databaseVersion: Int,
    val initialDBSql: () -> List<String>
) :
    SQLiteOpenHelper(context, null, null, databaseVersion) {
    override fun onCreate(db: SQLiteDatabase) {
        initialDBSql().forEach {
            db.execSQL(it.trimIndent())
        }
        Log.i("DB", dumpDatabaseSchema(db))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i("DB", "onUpgrade from $oldVersion to $newVersion")
        (oldVersion until newVersion).forEach {
            Log.i("DB", "   onUpgrade cycle version it")
            when (it) {
                // add photo to passwords
                1 -> DBHelper.upgradeFromV1ToV2AddPhoto(db, it)
                // delete useless lastdatetimeedit
                2 -> DBHelper.upgradeFromV2ToV3RemoveLastDateTimeEdit(db, it)
                // merge keys
                3 -> DBHelper.upgradeFromV3ToV4MergeKeys(db, it)
            }
            Log.i("DB$it", dumpDatabaseSchema(db))
        }
        // Upgrade database schema
        Log.i("DB", dumpDatabaseSchema(db))
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
