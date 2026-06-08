package fi.iki.ede.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import okio.Path
import okio.Path.Companion.toPath

import fi.iki.ede.crypto.keystore.KeyStoreHelper

actual fun getDatabaseBuilder(context: Any?): RoomDatabase.Builder<SafeDatabase> {
    val dbFile = File("safe.db")
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
    val rawText = "$privateKeyBase64\n$publicKeyBase64"
    val encryptedBytes = KeyStoreHelper.encryptWithDPAPI(rawText.toByteArray(Charsets.UTF_8))
    val tpmFile = File(System.getProperty("user.home"), ".safe_desktop_tpm_keys")
    tpmFile.writeBytes(encryptedBytes)
}

actual fun fetchTpmKeys(): Pair<String, String>? {
    val tpmFile = File(System.getProperty("user.home"), ".safe_desktop_tpm_keys")
    if (tpmFile.exists()) {
        try {
            val encryptedBytes = tpmFile.readBytes()
            val decryptedBytes = KeyStoreHelper.decryptWithDPAPI(encryptedBytes)
            val text = String(decryptedBytes, Charsets.UTF_8)
            val lines = text.lines()
            if (lines.size >= 2) {
                return Pair(lines[0], lines[1])
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    return null
}

actual fun initTpmKeys() {}
