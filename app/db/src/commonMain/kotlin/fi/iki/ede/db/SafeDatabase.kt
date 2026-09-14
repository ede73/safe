package fi.iki.ede.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.db.RoomConverters
import fi.iki.ede.db.cxf.CXFAccount
import fi.iki.ede.db.cxf.CxfAccountDao
import fi.iki.ede.db.cxf.CXFImport
import fi.iki.ede.db.cxf.CxfImportDao
import fi.iki.ede.db.cxf.CXFPasskey
import fi.iki.ede.db.cxf.CxfPasskeyDao
import fi.iki.ede.gpm.model.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Database(
    entities = [
        DecryptableCategoryEntry::class,
        DecryptableSiteEntry::class,
        KeyEntry::class,
        SavedGPM::class,
        SiteEntryGPMJoin::class,
        CXFAccount::class,
        CXFImport::class,
        CXFPasskey::class
    ],
    version = 10,
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
    abstract fun cxfAccountDao(): CxfAccountDao
    abstract fun cxfImportDao(): CxfImportDao
    abstract fun cxfPasskeyDao(): CxfPasskeyDao
}

expect object SafeDatabaseConstructor : RoomDatabaseConstructor<SafeDatabase>

const val DATABASE_NAME = "safe"

val MIGRATION_7_9 = object : Migration(7, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `cxf_account_id` TEXT NOT NULL, `email` TEXT NOT NULL, `imported_at` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_imports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `account_id` INTEGER NOT NULL, `cxf_item_id` TEXT NOT NULL, `cxf_account_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` BLOB NOT NULL, `url` BLOB NOT NULL, `username` BLOB NOT NULL, `password` BLOB NOT NULL, `raw_credential_json` BLOB NOT NULL, `note` BLOB NOT NULL, `created_at` INTEGER, `modified_at` INTEGER, `imported_at` INTEGER NOT NULL, `flagged_ignored` INTEGER NOT NULL, `hash` TEXT NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `cxf_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_imports_account_id` ON `cxf_imports` (`account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_imports_cxf_item_id` ON `cxf_imports` (`cxf_item_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_passkeys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `account_id` INTEGER NOT NULL, `cxf_item_id` TEXT NOT NULL, `cxf_account_id` TEXT NOT NULL, `rp_id` TEXT NOT NULL, `name` BLOB NOT NULL, `url` BLOB NOT NULL, `username` BLOB NOT NULL, `credential_id` BLOB NOT NULL, `user_handle` BLOB NOT NULL, `raw_credential_json` BLOB NOT NULL, `note` BLOB NOT NULL, `created_at` INTEGER, `modified_at` INTEGER, `imported_at` INTEGER NOT NULL, `flagged_ignored` INTEGER NOT NULL, `hash` TEXT NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `cxf_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_account_id` ON `cxf_passkeys` (`account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_cxf_item_id` ON `cxf_passkeys` (`cxf_item_id`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `cxf_account_id` TEXT NOT NULL, `email` TEXT NOT NULL, `imported_at` INTEGER NOT NULL)")
        connection.execSQL("DROP TABLE IF EXISTS `cxf_imports` ")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_imports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `account_id` INTEGER NOT NULL, `cxf_item_id` TEXT NOT NULL, `cxf_account_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` BLOB NOT NULL, `url` BLOB NOT NULL, `username` BLOB NOT NULL, `password` BLOB NOT NULL, `raw_credential_json` BLOB NOT NULL, `note` BLOB NOT NULL, `created_at` INTEGER, `modified_at` INTEGER, `imported_at` INTEGER NOT NULL, `flagged_ignored` INTEGER NOT NULL, `hash` TEXT NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `cxf_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_imports_account_id` ON `cxf_imports` (`account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_imports_cxf_item_id` ON `cxf_imports` (`cxf_item_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_passkeys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `account_id` INTEGER NOT NULL, `cxf_item_id` TEXT NOT NULL, `cxf_account_id` TEXT NOT NULL, `rp_id` TEXT NOT NULL, `name` BLOB NOT NULL, `url` BLOB NOT NULL, `username` BLOB NOT NULL, `credential_id` BLOB NOT NULL, `user_handle` BLOB NOT NULL, `raw_credential_json` BLOB NOT NULL, `note` BLOB NOT NULL, `created_at` INTEGER, `modified_at` INTEGER, `imported_at` INTEGER NOT NULL, `flagged_ignored` INTEGER NOT NULL, `hash` TEXT NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `cxf_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_account_id` ON `cxf_passkeys` (`account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_cxf_item_id` ON `cxf_passkeys` (`cxf_item_id`)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `cxf_passkeys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `account_id` INTEGER NOT NULL, `cxf_item_id` TEXT NOT NULL, `cxf_account_id` TEXT NOT NULL, `rp_id` TEXT NOT NULL, `name` BLOB NOT NULL, `url` BLOB NOT NULL, `username` BLOB NOT NULL, `credential_id` BLOB NOT NULL, `user_handle` BLOB NOT NULL, `raw_credential_json` BLOB NOT NULL, `note` BLOB NOT NULL, `created_at` INTEGER, `modified_at` INTEGER, `imported_at` INTEGER NOT NULL, `flagged_ignored` INTEGER NOT NULL, `hash` TEXT NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `cxf_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_account_id` ON `cxf_passkeys` (`account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_cxf_passkeys_cxf_item_id` ON `cxf_passkeys` (`cxf_item_id`)")
    }
}
