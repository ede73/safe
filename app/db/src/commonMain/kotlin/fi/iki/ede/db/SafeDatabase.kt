package fi.iki.ede.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.db.RoomConverters
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Database(
    entities = [
        DecryptableCategoryEntry::class,
        DecryptableSiteEntry::class,
        KeyEntry::class,
        SavedGPM::class,
        SiteEntryGPMJoin::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
@ConstructedBy(SafeDatabaseConstructor::class)
abstract class SafeDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun siteEntryDao(): SiteEntryDao
    abstract fun keyDao(): KeyDao
    abstract fun gpmDao(): GpmDao
    abstract fun siteEntryGPMJoinDao(): SiteEntryGPMJoinDao
}

expect object SafeDatabaseConstructor : RoomDatabaseConstructor<SafeDatabase>
