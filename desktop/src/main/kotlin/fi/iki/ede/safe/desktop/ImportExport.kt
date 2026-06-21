@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.Password
import fi.iki.ede.db.DBHelper
import fi.iki.ede.backup.RestoreDatabase
import fi.iki.ede.backup.BackupDatabase
import okio.Buffer

import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.db.SiteEntryGPMJoin
import kotlinx.coroutines.runBlocking

object BackupImporter {
    fun importFromXml(backupContent: String, passwordStr: String, db: DBHelper): Int {
        val restorer = RestoreDatabase()
        return restorer.doRestore(
            backupSource = Buffer().writeUtf8(backupContent),
            userPassword = Password(passwordStr),
            dbHelper = db,
            lastBackupDone = null,
            linkSaveGPMAndSiteEntry = { siteId, gpmId ->
                runBlocking {
                    db.database.siteEntryGPMJoinDao().insert(SiteEntryGPMJoin(siteId, gpmId))
                }
            },
            addSavedGPM = { savedGpm ->
                runBlocking {
                    db.database.gpmDao().insert(savedGpm)
                }
            },
            passwordLogin = { password ->
                val (salt, encryptedKey) = db.fetchSaltAndEncryptedMasterKey()
                val saltedPassword = SaltedPassword(salt, password)
                KeyStoreHelper.importExistingEncryptedMasterKey(saltedPassword, encryptedKey)
                true
            },
            reportProgress = { _, _, _ -> },
            verifyUserWantForOldBackup = { _, _ -> true }
        )
    }
}

object BackupExporter {
    fun exportToXml(db: DBHelper): String {
        val buffer = Buffer()
        val categories = db.fetchAllCategoryRows()
        val siteEntries = db.fetchAllRows()
        val softDeleted = db.fetchAllRows(softDeletedOnly = true)
        val gpmItems = runBlocking { db.database.gpmDao().getAll().toSet() }
        val joinItems = runBlocking { db.database.siteEntryGPMJoinDao().getAll() }
        val mappings = joinItems.groupBy({ it.passwordId }, { it.gpmId })
            .mapValues { (_, values) -> values.toSet() }

        BackupDatabase.backup(
            categoriesList = categories,
            softDeletedEntries = softDeleted.toSet(),
            getSiteEntriesOfCategory = { categoryId -> siteEntries.filter { it.categoryId == categoryId } },
            siteEntryGPMMappings = mappings,
            allSavedGPMs = gpmItems,
            finalSink = buffer
        )
        return buffer.readUtf8()
    }
}
