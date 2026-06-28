@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fi.iki.ede.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<SafeDatabase> {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val path = requireNotNull(documentDirectory?.path)
    val dbFilePath = "$path/$databaseName.db"
    return Room.databaseBuilder<SafeDatabase>(
        name = dbFilePath
    ).setDriver(BundledSQLiteDriver())
}

actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<SafeDatabase> {
    return Room.inMemoryDatabaseBuilder<SafeDatabase>()
        .setDriver(BundledSQLiteDriver())
}

actual fun getPhotoDir(): Path {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    val path = requireNotNull(documentDirectory?.path)
    return "$path/photos".toPath()
}

actual fun beginTransaction(database: SafeDatabase) {}
actual fun setTransactionSuccessful(database: SafeDatabase) {}
actual fun endTransaction(database: SafeDatabase) {}

actual fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {}
actual fun fetchTpmKeys(): Pair<String, String>? = null
actual fun initTpmKeys() {}
