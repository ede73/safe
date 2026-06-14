package fi.iki.ede.safe

import android.content.Context
import java.io.File
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.runLegacyDatabaseMigration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

@RunWith(AndroidJUnit4::class)
@ExperimentalTime
class RoomMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Clean up any existing database states
        context.deleteDatabase("safe")
        context.deleteDatabase("safe_legacy")
        
        val prefs = context.getSharedPreferences("safe_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("room_migration_done").apply()
    }

    @Test
    fun testLegacyMigrationToRoom() {
        // 1. Create legacy database on disk
        val legacyHelper = LegacyOpenHelper(context)
        val legacyDb = legacyHelper.writableDatabase
        
        // Insert a test category
        legacyDb.execSQL("INSERT INTO categories (id, name) VALUES (1, X'11223344556677889900112233445566')")
        
        // Insert a test password
        legacyDb.execSQL("""
            INSERT INTO passwords (id, category, password, description, username, website, note, photo, passwordchangeddate, deleted, extensions)
            VALUES (10, 1, X'0102030405060708090a0b0c0d0e0f10', X'aabbccddeeff00112233445566778899', X'001122', X'334455', X'667788', 'photo_filename.jpg', '1600000000', 0, X'ffeedd')
        """.trimIndent())
        
        // Insert a test key
        legacyDb.execSQL("INSERT INTO keys (encryptedkey, salt) VALUES (X'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', X'bbbbbbbbbbbbbbbb')")
        
        legacyHelper.close()

        // Verify that the database file exists on disk
        val dbFile = context.getDatabasePath("safe")
        assertTrue(dbFile.exists())

        // 2. Execute migration
        runLegacyDatabaseMigration(context)

        // 3. Verify that safe_legacy was deleted
        val legacyDbFile = context.getDatabasePath("safe_legacy")
        assertFalse(legacyDbFile.exists())

        // 4. Open with DBHelper and assert parity
        val dbHelper = DBHelper(context = context)
        
        // Verify Categories
        val categories = dbHelper.fetchAllCategoryRows()
        assertEquals(1, categories.size)
        assertEquals(1L, categories[0].id)
        assertArrayEquals(
            byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte(), 0x99.toByte(), 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66),
            categories[0].encryptedName.combineIVAndCipherText()
        )

        // Verify Passwords
        val siteEntries = dbHelper.fetchAllRows()
        assertEquals(1, siteEntries.size)
        val entry = siteEntries[0]
        assertEquals(10L, entry.id)
        assertEquals(1L, entry.categoryId)
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10),
            entry.password.combineIVAndCipherText()
        )
        assertArrayEquals(
            byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte(), 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte(), 0x99.toByte()),
            entry.description.combineIVAndCipherText()
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x11, 0x22),
            entry.username.combineIVAndCipherText()
        )
        assertArrayEquals(
            byteArrayOf(0x33, 0x44, 0x55),
            entry.website.combineIVAndCipherText()
        )
        assertArrayEquals(
            byteArrayOf(0x66, 0x77, 0x88.toByte()),
            entry.note.combineIVAndCipherText()
        )
        assertEquals("photo_filename.jpg", entry.photoFilename)
        assertEquals(1600000000L, entry.passwordChangedDate?.epochSeconds)
        assertEquals(0L, entry.deleted)
        assertArrayEquals(
            byteArrayOf(0xff.toByte(), 0xee.toByte(), 0xdd.toByte()),
            entry.extensions.combineIVAndCipherText()
        )

        // Verify Keys
        val (salt, key) = dbHelper.fetchSaltAndEncryptedMasterKey()
        assertArrayEquals(
            byteArrayOf(0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte(), 0xbb.toByte()),
            salt.salt
        )
        assertArrayEquals(
            byteArrayOf(0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte(), 0xaa.toByte()),
            key.combineIVAndCipherText()
        )
    }

    @Test
    fun testLegacyMigrationWithBlobPhoto() {
        // 1. Create legacy database on disk
        val legacyHelper = LegacyOpenHelper(context)
        val legacyDb = legacyHelper.writableDatabase
        
        // Insert a test category
        legacyDb.execSQL("INSERT INTO categories (id, name) VALUES (1, X'11223344556677889900112233445566')")
        
        // Insert a test password with binary BLOB photo
        legacyDb.execSQL("""
            INSERT INTO passwords (id, category, password, description, username, website, note, photo, passwordchangeddate, deleted, extensions)
            VALUES (10, 1, X'0102030405060708090a0b0c0d0e0f10', X'aabbccddeeff00112233445566778899', X'001122', X'334455', X'667788', X'99887766554433221100', '1600000000', 0, X'ffeedd')
        """.trimIndent())
        
        // Insert a test key
        legacyDb.execSQL("INSERT INTO keys (encryptedkey, salt) VALUES (X'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', X'bbbbbbbbbbbbbbbb')")
        
        legacyHelper.close()

        // 2. Execute migration
        runLegacyDatabaseMigration(context)

        // 3. Open with DBHelper and assert parity
        val dbHelper = DBHelper(context = context)
        val siteEntries = dbHelper.fetchAllRows()
        assertEquals(1, siteEntries.size)
        val entry = siteEntries[0]
        assertEquals(10L, entry.id)
        
        // Assert filename exists and the file has the correct binary bytes
        assertNotNull(entry.photoFilename)
        assertTrue(entry.photoFilename!!.endsWith(".photo_data"))
        val photoFile = File(File(context.filesDir, "photos"), entry.photoFilename!!)
        assertTrue(photoFile.exists())
        assertArrayEquals(
            byteArrayOf(0x99.toByte(), 0x88.toByte(), 0x77.toByte(), 0x66.toByte(), 0x55.toByte(), 0x44.toByte(), 0x33.toByte(), 0x22.toByte(), 0x11.toByte(), 0x00.toByte()),
            photoFile.readBytes()
        )
    }

    @Test
    fun testLegacyMigrationFailureRollsBack() {
        // 1. Create legacy database on disk
        val legacyHelper = LegacyOpenHelper(context)
        val legacyDb = legacyHelper.writableDatabase
        
        // Insert invalid legacy entry that causes Room insert crash (e.g. missing primary key in categories)
        legacyDb.execSQL("INSERT INTO categories (id, name) VALUES (1, X'11223344556677889900112233445566')")
        // Passwords has category = 1, but we don't insert a key or categories correctly to force a constraint crash, or insert null into non-null column
        legacyDb.execSQL("""
            INSERT INTO passwords (id, category, password, description, username, website, note, photo, passwordchangeddate, deleted, extensions)
            VALUES (10, 1, NULL, X'aabbccddeeff00112233445566778899', X'001122', X'334455', X'667788', 'photo.jpg', '1600000000', 0, X'ffeedd')
        """.trimIndent())
        
        legacyHelper.close()

        // 2. Execute migration - should throw error but roll back legacy file successfully
        try {
            runLegacyDatabaseMigration(context)
            fail("Expected migration to fail due to NULL password")
        } catch (e: Throwable) {
            // Expected
        }

        // 3. Verify rollback - "safe" database should be original legacy DB and "safe_legacy" must not exist
        val dbFile = context.getDatabasePath("safe")
        val legacyDbFile = context.getDatabasePath("safe_legacy")
        assertTrue(dbFile.exists())
        assertFalse(legacyDbFile.exists())

        // Ensure we can open the database as standard legacy SQLite again
        val dbCheck = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        dbCheck.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'", null).use { cursor ->
            assertEquals(0, cursor.count)
        }
        dbCheck.close()
    }
}

class LegacyOpenHelper(
    context: Context,
    databaseName: String = "safe"
) : SQLiteOpenHelper(context, databaseName, null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """.trimIndent())
        
        db.execSQL("""
            CREATE TABLE passwords (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category INTEGER NOT NULL,
                password TEXT NOT NULL,
                description TEXT NOT NULL,
                username TEXT,
                website TEXT,
                note TEXT,
                photo TEXT,
                passwordchangeddate TEXT,
                deleted INTEGER DEFAULT 0,
                extensions TEXT
            )
        """.trimIndent())
        
        db.execSQL("""
            CREATE TABLE keys (
                encryptedkey TEXT NOT NULL,
                salt TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
