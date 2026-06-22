@file:OptIn(kotlin.time.ExperimentalTime::class)

package fi.iki.ede.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.Path
import okio.Path.Companion.toPath

internal const val PREFS_NAME = "safe_prefs"
internal const val PREF_ROOM_MIGRATION_DONE = "room_migration_done"

private var appContext: Context? = null

fun setDatabaseContext(context: Context) {
    appContext = context.applicationContext
}

@Suppress("DEPRECATION")
fun runLegacyDatabaseMigration(context: Context, databaseName: String = DBHelper.DATABASE_NAME) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val migrationDone = prefs.getBoolean(PREF_ROOM_MIGRATION_DONE, false)
    if (migrationDone) return

    val dbFile = context.getDatabasePath(databaseName)
    if (dbFile.exists()) {
        var isLegacy = false
        try {
            val dbCheck = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            dbCheck.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'", null).use { cursor ->
                isLegacy = cursor.count == 0
            }
            dbCheck.close()
        } catch (e: Exception) {
            // Fail-safe: if we can't read it, do not attempt custom migration
        }

        if (isLegacy) {
            val legacyDbFile = context.getDatabasePath("safe_legacy")
            val journalFile = File(dbFile.absolutePath + "-journal")
            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")
            
            val legacyJournalFile = File(legacyDbFile.absolutePath + "-journal")
            val legacyWalFile = File(legacyDbFile.absolutePath + "-wal")
            val legacyShmFile = File(legacyDbFile.absolutePath + "-shm")

            if (legacyDbFile.exists()) {
                context.deleteDatabase("safe_legacy")
            }

            if (dbFile.renameTo(legacyDbFile)) {
                if (journalFile.exists()) journalFile.renameTo(legacyJournalFile)
                if (walFile.exists()) walFile.renameTo(legacyWalFile)
                if (shmFile.exists()) shmFile.renameTo(legacyShmFile)

                // Initialize Room to create a clean empty database file
                val tempDb = Room.databaseBuilder<SafeDatabase>(
                    context = context,
                    name = DATABASE_NAME
                ).setDriver(BundledSQLiteDriver()).build()

                val photoDir = File(context.filesDir, "photos")
                if (!photoDir.exists()) {
                    photoDir.mkdirs()
                }

                var migrationSuccessful = false
                try {
                    val legacyDb = SQLiteDatabase.openDatabase(legacyDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    
                    runBlocking {
                        // 1. Copy categories
                        legacyDb.rawQuery("SELECT * FROM categories", null).use { cursor ->
                            val idIdx = cursor.getColumnIndex("id")
                            val nameIdx = cursor.getColumnIndex("name")
                            while (cursor.moveToNext()) {
                                val catId = if (idIdx != -1) cursor.getLong(idIdx) else continue
                                val nameBytes = if (nameIdx != -1 && !cursor.isNull(nameIdx)) cursor.getBlob(nameIdx) else null
                                if (nameBytes != null) {
                                    tempDb.categoryDao().insert(DecryptableCategoryEntry().apply {
                                        id = catId
                                        encryptedName = IVCipherText(16, nameBytes)
                                    })
                                }
                            }
                        }

                        // 2. Copy passwords
                        legacyDb.rawQuery("SELECT * FROM passwords", null).use { cursor ->
                            val idIdx = cursor.getColumnIndex("id")
                            val categoryIdx = cursor.getColumnIndex("category")
                            val passwordIdx = cursor.getColumnIndex("password")
                            val descriptionIdx = cursor.getColumnIndex("description")
                            val usernameIdx = cursor.getColumnIndex("username")
                            val websiteIdx = cursor.getColumnIndex("website")
                            val noteIdx = cursor.getColumnIndex("note")
                            val photoIdx = cursor.getColumnIndex("photo")
                            val passwordchangeddateIdx = cursor.getColumnIndex("passwordchangeddate")
                            val deletedIdx = cursor.getColumnIndex("deleted")
                            val extensionsIdx = cursor.getColumnIndex("extensions")

                            while (cursor.moveToNext()) {
                                val siteId = if (idIdx != -1) cursor.getLong(idIdx) else continue
                                val catId = if (categoryIdx != -1) cursor.getLong(categoryIdx) else 0L
                                val pwdBytes = if (passwordIdx != -1 && !cursor.isNull(passwordIdx)) cursor.getBlob(passwordIdx) else null
                                val descBytes = if (descriptionIdx != -1 && !cursor.isNull(descriptionIdx)) cursor.getBlob(descriptionIdx) else null

                                if (pwdBytes != null && descBytes != null) {
                                    val userBytes = if (usernameIdx != -1 && !cursor.isNull(usernameIdx)) cursor.getBlob(usernameIdx) else null
                                    val webBytes = if (websiteIdx != -1 && !cursor.isNull(websiteIdx)) cursor.getBlob(websiteIdx) else null
                                    val noteBytes = if (noteIdx != -1 && !cursor.isNull(noteIdx)) cursor.getBlob(noteIdx) else null

                                    val photoFilenameVal = try {
                                        if (photoIdx != -1 && !cursor.isNull(photoIdx)) {
                                            val photoType = cursor.getType(photoIdx)
                                            if (photoType == Cursor.FIELD_TYPE_BLOB) {
                                                val photoBytes = cursor.getBlob(photoIdx)
                                                if (photoBytes != null && photoBytes.isNotEmpty()) {
                                                    val randomName = "%016x%016x.photo_data".format(
                                                        java.util.Random().nextLong(),
                                                        java.util.Random().nextLong()
                                                    )
                                                    val photoFile = File(photoDir, randomName)
                                                    java.io.FileOutputStream(photoFile).use { fos ->
                                                        fos.write(photoBytes)
                                                    }
                                                    randomName
                                                } else null
                                            } else if (photoType == Cursor.FIELD_TYPE_STRING) {
                                                cursor.getString(photoIdx)
                                            } else null
                                        } else null
                                    } catch (e: Exception) {
                                        android.util.Log.e("SafeDatabaseBuilder", "Ignoring broken photo entry on row $siteId", e)
                                        null
                                    }

                                    val dateVal = if (passwordchangeddateIdx != -1 && !cursor.isNull(passwordchangeddateIdx)) cursor.getLong(passwordchangeddateIdx) else 0L
                                    val deletedVal = if (deletedIdx != -1 && !cursor.isNull(deletedIdx)) cursor.getLong(deletedIdx) else 0L
                                    val extBytes = if (extensionsIdx != -1 && !cursor.isNull(extensionsIdx)) cursor.getBlob(extensionsIdx) else null

                                    tempDb.siteEntryDao().insert(DecryptableSiteEntry(catId).apply {
                                        id = siteId
                                        password = IVCipherText(16, pwdBytes)
                                        description = IVCipherText(16, descBytes)
                                        username = if (userBytes != null) IVCipherText(16, userBytes) else IVCipherText.getEmpty()
                                        website = if (webBytes != null) IVCipherText(16, webBytes) else IVCipherText.getEmpty()
                                        note = if (noteBytes != null) IVCipherText(16, noteBytes) else IVCipherText.getEmpty()
                                        photoFilename = photoFilenameVal
                                        passwordChangedDate = if (dateVal != 0L) kotlin.time.Instant.fromEpochSeconds(dateVal) else null
                                        deleted = deletedVal
                                        extensions = if (extBytes != null) IVCipherText(16, extBytes) else IVCipherText.getEmpty()
                                    })
                                }
                            }
                        }

                        // 3. Copy keys
                        legacyDb.rawQuery("SELECT * FROM keys", null).use { cursor ->
                            val keyIdx = cursor.getColumnIndex("encryptedkey")
                            val saltIdx = cursor.getColumnIndex("salt")
                            while (cursor.moveToNext()) {
                                val keyBytes = if (keyIdx != -1 && !cursor.isNull(keyIdx)) cursor.getBlob(keyIdx) else null
                                val saltBytes = if (saltIdx != -1 && !cursor.isNull(saltIdx)) cursor.getBlob(saltIdx) else null
                                if (keyBytes != null && saltBytes != null) {
                                    tempDb.keyDao().insert(KeyEntry(keyBytes, saltBytes))
                                }
                            }
                        }
                    }
                    
                    legacyDb.close()
                    migrationSuccessful = true
                } catch (t: Throwable) {
                    android.util.Log.e("SafeDatabaseBuilder", "Migration failed, rolling back", t)
                    throw t
                } finally {
                    tempDb.close()
                    if (!migrationSuccessful) {
                        context.deleteDatabase(DATABASE_NAME)
                        if (legacyDbFile.exists()) {
                            legacyDbFile.renameTo(dbFile)
                        }
                        if (legacyJournalFile.exists()) {
                            legacyJournalFile.renameTo(journalFile)
                        }
                        if (legacyWalFile.exists()) {
                            legacyWalFile.renameTo(walFile)
                        }
                        if (legacyShmFile.exists()) {
                            legacyShmFile.renameTo(shmFile)
                        }
                    } else {
                        context.deleteDatabase("safe_legacy")
                    }
                }
            }
        }
    }
    
    prefs.edit().putBoolean(PREF_ROOM_MIGRATION_DONE, true).apply()
}

actual fun getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<SafeDatabase> {
    val context = appContext ?: throw IllegalStateException("Database context not initialized. Call setDatabaseContext(context) first.")
    runLegacyDatabaseMigration(context, databaseName)
    return Room.databaseBuilder<SafeDatabase>(
        context = context,
        name = databaseName
    )
}

actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<SafeDatabase> {
    val context = appContext ?: throw IllegalStateException("Database context not initialized. Call setDatabaseContext(context) first.")
    return Room.inMemoryDatabaseBuilder<SafeDatabase>(
        context = context
    )
}

actual fun getPhotoDir(): Path {
    val context = appContext ?: throw IllegalStateException("Database context not initialized. Call setDatabaseContext(context) first.")
    return (context.filesDir.absolutePath.toPath() / "photos")
}

@Suppress("DEPRECATION")
actual fun beginTransaction(database: SafeDatabase) {
    database.beginTransaction()
}

@Suppress("DEPRECATION")
actual fun setTransactionSuccessful(database: SafeDatabase) {
    database.setTransactionSuccessful()
}

@Suppress("DEPRECATION")
actual fun endTransaction(database: SafeDatabase) {
    database.endTransaction()
}

actual fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {}
actual fun fetchTpmKeys(): Pair<String, String>? = null
actual fun initTpmKeys() {}
