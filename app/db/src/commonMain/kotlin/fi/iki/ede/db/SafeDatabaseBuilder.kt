package fi.iki.ede.db

import androidx.room.RoomDatabase
import okio.Path

expect fun getDatabaseBuilder(context: Any?, databaseName: String): RoomDatabase.Builder<SafeDatabase>
expect fun getInMemoryDatabaseBuilder(context: Any?): RoomDatabase.Builder<SafeDatabase>
expect fun getPhotoDir(context: Any?): Path

expect fun beginTransaction(database: SafeDatabase)
expect fun setTransactionSuccessful(database: SafeDatabase)
expect fun endTransaction(database: SafeDatabase)

expect fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String)
expect fun fetchTpmKeys(): Pair<String, String>?
expect fun initTpmKeys()
