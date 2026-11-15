@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.SyncTombstone
import fi.iki.ede.safe.ui.sync.ConflictType
import fi.iki.ede.safe.ui.sync.SyncConflict
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncItem
import fi.iki.ede.cryptoobjects.plainPassword
import fi.iki.ede.cryptoobjects.plainExtensions
import fi.iki.ede.preferences.Preferences
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncTransactionTest {
    private lateinit var db: DBHelper

    @BeforeEach
    fun setUp() {
        KeystoreHelperMock4UnitTests.mock()
        Preferences.setVaultClusterSecret("ab".repeat(32))
        db = DBHelper(databaseName = null)
        DBHelperFactory.initializeDatabase(db)
        db.clearAllData()
    }

    @AfterEach
    fun tearDown() {
        Preferences.setVaultClusterSecret(null)
        db.database.close()
        DBHelperFactory.clearDatabase()
        unmockkAll()
    }

    @Test
    fun failedBatchRollsBackAllEntriesAndCategories() {
        val good = SyncItem(cxfItemId = "good", name = "Good", password = "secret")
        val bad = SyncItem(cxfItemId = "bad", name = "Bad", kind = "UNSUPPORTED")
        val changes = listOf(
            SyncConflict("good", ConflictType.NEW_ENTRY_ADDED, null, good),
            SyncConflict("bad", ConflictType.NEW_ENTRY_ADDED, null, bad)
        )
        assertThrows(IllegalArgumentException::class.java) {
            SyncConflictResolver.applyResolvedConflictsToLocalDatabase(changes)
        }
        assertEquals(0, runBlocking { db.database.siteEntryDao().getAllActive().size })
        assertEquals(0, runBlocking { db.database.categoryDao().getAll().size })
    }

    @Test
    fun divergentPasswordKeepsLosingVersionExactlyOnce() {
        val local = SyncItem(cxfItemId = "shared-id", name = "Example", password = "first", modifiedAt = 10)
        val remote = local.copy(password = "second", modifiedAt = 20)
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(
            listOf(SyncConflict("shared-id", ConflictType.NEW_ENTRY_ADDED, null, local))
        )
        val conflict = SyncConflictResolver.analyzeConflicts(listOf(local), listOf(remote)).single()
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(listOf(conflict))
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(listOf(conflict))

        val rows = runBlocking { db.database.siteEntryDao().getAllActive() }
        assertEquals(2, rows.size)
        assertEquals(setOf("first", "second"), rows.map { it.plainPassword }.toSet())
        assertEquals(2, rows.map { it.plainExtensions["cxfItemId"]?.single() }.toSet().size)
    }

    @Test
    fun independentlyCreatedEquivalentPasswordGetsSharedSyncId() {
        val local = SyncItem(
            cxfItemId = "site-entry:local-row", name = "Example", username = "alice",
            password = "secret", url = "https://example.test", categoryName = "Personal", modifiedAt = 10
        )
        val remote = local.copy(cxfItemId = "site-entry:remote-row")
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(
            listOf(SyncConflict(local.cxfItemId, ConflictType.NEW_ENTRY_ADDED, null, local))
        )

        val readLocal = SyncConflictResolver.readLocalDatabaseAsSyncItems().single()
        val bootstrap = SyncConflictResolver.analyzeConflicts(listOf(readLocal), listOf(remote)).single()
        assertTrue(bootstrap.conflictId.startsWith("legacy-content:"))
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(listOf(bootstrap))

        val normalized = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        assertEquals(1, normalized.size)
        assertEquals(bootstrap.conflictId, normalized.single().cxfItemId)
        assertEquals("secret", normalized.single().password)
    }

    @Test
    fun hardPurgeKeepsOnlyIdentityTombstoneForSync() {
        val item = SyncItem("shared-id", "Example", "alice", "secret", "https://example.test")
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(
            listOf(SyncConflict(item.cxfItemId, ConflictType.NEW_ENTRY_ADDED, null, item))
        )
        val rowId = runBlocking { db.database.siteEntryDao().getAllActive().single().id!! }

        assertEquals(1, db.hardDeleteSiteEntry(rowId))
        assertEquals(0, runBlocking { db.database.siteEntryDao().getAllActive().size })
        val snapshot = SyncConflictResolver.readLocalDatabaseAsSyncItems().single()
        assertEquals("shared-id", snapshot.cxfItemId)
        assertTrue(snapshot.isDeleted && snapshot.isPermanentDeletion)
        assertEquals("", snapshot.password, "A permanent tombstone must not retain the deleted password")
    }

    @Test
    fun permanentDeletionFromPeerPurgesLocalRowAndNewerEditClearsTombstone() {
        val item = SyncItem("shared-id", "Example", "alice", "secret", modifiedAt = 1_700_000_000_000)
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(
            listOf(SyncConflict(item.cxfItemId, ConflictType.NEW_ENTRY_ADDED, null, item))
        )
        val tombstone = item.copy(
            name = "", username = "", password = "", isDeleted = true,
            isPermanentDeletion = true, modifiedAt = 1_700_000_002_000
        )
        val deletionConflict = SyncConflictResolver.analyzeConflicts(
            SyncConflictResolver.readLocalDatabaseAsSyncItems(), listOf(tombstone)
        ).single()
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(listOf(deletionConflict))
        assertEquals(0, runBlocking { db.database.siteEntryDao().getAllActive().size })
        assertTrue(SyncConflictResolver.readLocalDatabaseAsSyncItems().single().isPermanentDeletion)

        val laterEdit = item.copy(password = "new-secret", modifiedAt = 1_700_000_003_000)
        val restoreConflict = SyncConflictResolver.analyzeConflicts(
            SyncConflictResolver.readLocalDatabaseAsSyncItems(), listOf(laterEdit)
        ).single()
        assertEquals(false, restoreConflict.selectedIsDeleted)
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(listOf(restoreConflict))
        val finalSnapshot = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        assertEquals(1, finalSnapshot.size)
        assertEquals("new-secret", finalSnapshot.single().password)
        assertFalse(finalSnapshot.single().isPermanentDeletion)
    }

    @Test
    fun cxfImportAndPasskeyMetadataRoundTripThroughSync() {
        val account = "account-id"
        val imported = SyncItem(
            cxfItemId = "cxf-import:$account:source:hash-a", kind = "CXF_IMPORT",
            cxfAccountId = account, cxfAccountEmail = "alice@example.com",
            sourceCxfItemId = "source", cxfHash = "hash-a", cxfType = "login",
            name = "Example", username = "alice", password = "secret", url = "https://example.com"
        )
        val passkey = SyncItem(
            cxfItemId = "cxf-passkey:$account:key:hash-b", kind = "CXF_PASSKEY",
            cxfAccountId = account, cxfAccountEmail = "alice@example.com",
            sourceCxfItemId = "key", cxfHash = "hash-b", name = "Passkey",
            rpId = "example.com", credentialId = "credential", userHandle = "alice"
        )
        SyncConflictResolver.applyResolvedConflictsToLocalDatabase(
            listOf(imported, passkey).map { item ->
                SyncConflict(item.cxfItemId, ConflictType.NEW_ENTRY_ADDED, null, item)
            }
        )
        val readBack = SyncConflictResolver.readLocalDatabaseAsSyncItems()
        assertEquals(setOf("CXF_ACCOUNT", "CXF_IMPORT", "CXF_PASSKEY"), readBack.map { it.kind }.toSet())
        assertEquals("secret", readBack.single { it.kind == "CXF_IMPORT" }.password)
        assertEquals("credential", readBack.single { it.kind == "CXF_PASSKEY" }.credentialId)
    }

    @Test
    fun revokedClusterSecretCannotBeSilentlyReused() {
        val oldSecret = "a".repeat(64)
        Preferences.setVaultClusterSecret(oldSecret)
        Preferences.addPairedSyncDevice("peer", "Peer")
        Preferences.removePairedSyncDevice("peer")

        assertNull(Preferences.getVaultClusterSecret())
        assertTrue(Preferences.isRevokedVaultClusterSecret(oldSecret))
        assertTrue(Preferences.getPairedSyncDevices().isEmpty())
    }
}
