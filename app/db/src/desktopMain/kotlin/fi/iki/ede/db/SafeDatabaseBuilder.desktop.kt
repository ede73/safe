package fi.iki.ede.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import fi.iki.ede.crypto.keystore.KeyStoreHelper

actual fun getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<SafeDatabase> {
    val parent = FileSystem.SYSTEM.canonicalize(".".toPath())
    val dbPath = parent / "$databaseName.db"
    return Room.databaseBuilder<SafeDatabase>(
        name = dbPath.toString()
    ).setDriver(BundledSQLiteDriver())
}

actual fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<SafeDatabase> {
    return Room.inMemoryDatabaseBuilder<SafeDatabase>()
        .setDriver(BundledSQLiteDriver())
}

actual fun getPhotoDir(): Path {
    return "photos".toPath()
}

actual fun beginTransaction(database: SafeDatabase) {}
actual fun setTransactionSuccessful(database: SafeDatabase) {}
actual fun endTransaction(database: SafeDatabase) {}

actual fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {
    // On Windows/Desktop, we do not store private keys on disk.
}

@OptIn(ExperimentalEncodingApi::class)
actual fun fetchTpmKeys(): Pair<String, String>? {
    try {
        KeyStoreHelper.ensureMockKeysLoaded()
        val privKey = KeyStoreHelper.getLoadedPrivateKey()
        val pubKey = KeyStoreHelper.getLoadedPublicKey()
        if (privKey != null && pubKey != null) {
            val privBase64 = Base64.encode(privKey.encoded)
            val pubBase64 = Base64.encode(pubKey.encoded)
            return Pair(privBase64, pubBase64)
        }
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

actual fun initTpmKeys() {}
