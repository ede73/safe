package fi.iki.ede.db

import androidx.room.RoomDatabase
import okio.Path

expect fun getDatabaseBuilder(databaseName: String): RoomDatabase.Builder<SafeDatabase>
expect fun getInMemoryDatabaseBuilder(): RoomDatabase.Builder<SafeDatabase>
expect fun getPhotoDir(): Path

expect fun beginTransaction(database: SafeDatabase)
expect fun setTransactionSuccessful(database: SafeDatabase)
expect fun endTransaction(database: SafeDatabase)

expect fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String)
expect fun fetchTpmKeys(): Pair<String, String>?
expect fun initTpmKeys()
