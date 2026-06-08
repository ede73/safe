@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.db.DBHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ImportExportTest {
    private val dbFile = File("safe_db.json")
    private val backupFile = File("safe_db.json.bak")
    private lateinit var db: DBHelper

    @BeforeEach
    fun setUp() {
        if (dbFile.exists()) {
            dbFile.renameTo(backupFile)
        }
        db = DBHelper()
        // Initialize master password and keys
        val password = "qwertyuiop"
        try {
            val (salt, cipheredKey) = fi.iki.ede.crypto.keystore.KeyStoreHelper.createNewKey(fi.iki.ede.crypto.Password(password))
            db.storeSaltAndEncryptedMasterKey(salt, cipheredKey)
        } catch (e: Exception) {
            // Already initialized or mock setup is enough
        }
        db.clearAllData()
    }

    @AfterEach
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
        if (backupFile.exists()) {
            backupFile.renameTo(dbFile)
        }
    }

    @Test
    fun testImportExportRoundTrip() {
        val originalXmlFile = File("../2categories.xml")
        val xmlFile = if (originalXmlFile.exists()) originalXmlFile else File("2categories.xml")
        
        assertTrue(xmlFile.exists(), "2categories.xml must exist for this test to run")

        val originalBackupContent = xmlFile.readText()
        val password = "qwertyuiop"

        // 1. Import original backup XML
        val importedCount = BackupImporter.importFromXml(originalBackupContent, password, db)
        assertTrue(importedCount > 0, "Should import at least one password entry")

        val categoriesBefore = db.fetchAllCategoryRows()
        val siteEntriesBefore = db.fetchAllRows()

        // Verify categories and site entries are loaded
        assertEquals(2, categoriesBefore.size, "Should import 2 categories")
        assertEquals(3, siteEntriesBefore.size, "Should import 3 site entries")

        // 2. Export to XML
        val exportedBackupContent = BackupExporter.exportToXml(db)
        assertNotNull(exportedBackupContent)
        assertTrue(exportedBackupContent.lines().size >= 5, "Exported backup should have at least 5 lines")

        // 3. Clear database
        db.clearAllData()
        db.skipPrepopulate = true
        assertEquals(0, db.fetchAllCategoryRows().size)
        assertEquals(0, db.fetchAllRows().size)
        db.skipPrepopulate = false

        // 4. Import the exported backup XML
        val reImportedCount = BackupImporter.importFromXml(exportedBackupContent, password, db)
        assertEquals(importedCount, reImportedCount, "Re-imported count should match originally imported count")

        val categoriesAfter = db.fetchAllCategoryRows()
        val siteEntriesAfter = db.fetchAllRows()

        // 5. Assert equivalence
        assertEquals(categoriesBefore.size, categoriesAfter.size)
        for (i in categoriesBefore.indices) {
            val before = categoriesBefore[i]
            val after = categoriesAfter.firstOrNull { it.plainName == before.plainName }
            assertNotNull(after, "Category '${before.plainName}' was not preserved")
        }

        assertEquals(siteEntriesBefore.size, siteEntriesAfter.size)
        for (before in siteEntriesBefore) {
            val after = requireNotNull(siteEntriesAfter.firstOrNull { it.cachedPlainDescription == before.cachedPlainDescription }) {
                "Site entry '${before.cachedPlainDescription}' was not preserved"
            }
            
            assertEquals(before.plainUsername, after.plainUsername)
            assertEquals(before.plainPassword, after.plainPassword)
            assertEquals(before.plainWebsite, after.plainWebsite)
            assertEquals(before.plainNote, after.plainNote)
            assertEquals(before.deleted, after.deleted)
            assertEquals(before.photo, after.photo)
            assertEquals(before.extensions, after.extensions)
            assertEquals(before.passwordChangedDate, after.passwordChangedDate)
        }
    }
}
