package fi.iki.ede.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import okio.Path
import okio.Path.Companion.toPath

actual fun getDatabaseBuilder(context: Any?): RoomDatabase.Builder<SafeDatabase> {
    // Addressed PR7 comment: Use common DATABASE_NAME constant instead of hardcoding "safe.db"
    val dbFile = File("$DATABASE_NAME.db")
    return Room.databaseBuilder<SafeDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
}

actual fun getInMemoryDatabaseBuilder(context: Any?): RoomDatabase.Builder<SafeDatabase> {
    return Room.inMemoryDatabaseBuilder<SafeDatabase>()
        .setDriver(BundledSQLiteDriver())
}

actual fun getPhotoDir(context: Any?): Path {
    return "photos".toPath()
}

actual fun beginTransaction(database: SafeDatabase) {}
actual fun setTransactionSuccessful(database: SafeDatabase) {}
actual fun endTransaction(database: SafeDatabase) {}

actual fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {
    File("tpm_keys.txt").writeText("$privateKeyBase64\n$publicKeyBase64")
}

actual fun fetchTpmKeys(): Pair<String, String>? {
    val txtFile = File("tpm_keys.txt")
    if (txtFile.exists()) {
        val lines = txtFile.readLines()
        if (lines.size >= 2) {
            return Pair(lines[0], lines[1])
        }
    }
    return null
}

actual fun initTpmKeys() {}
