package fi.iki.ede.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import okio.Path
import okio.Path.Companion.toPath
import java.util.Base64
import fi.iki.ede.crypto.keystore.KeyStoreHelper

actual fun getDatabaseBuilder(context: Any?): RoomDatabase.Builder<SafeDatabase> {
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
    // On Windows/Desktop, we do not store private keys on disk.
}

actual fun fetchTpmKeys(): Pair<String, String>? {
    try {
        KeyStoreHelper.ensureMockKeysLoaded()
        val privKey = KeyStoreHelper.getLoadedPrivateKey()
        val pubKey = KeyStoreHelper.getLoadedPublicKey()
        if (privKey != null && pubKey != null) {
            val privBase64 = Base64.getEncoder().encodeToString(privKey.encoded)
            val pubBase64 = Base64.getEncoder().encodeToString(pubKey.encoded)
            return Pair(privBase64, pubBase64)
        }
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

actual fun initTpmKeys() {}
