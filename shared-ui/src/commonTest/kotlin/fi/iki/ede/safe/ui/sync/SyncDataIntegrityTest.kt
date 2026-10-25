package fi.iki.ede.safe.ui.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.serialization.encodeToString

class SyncDataIntegrityTest {
    @Test
    fun urlOnlyChangeChangesVaultRoot() {
        val original = SyncItem(cxfItemId = "id-1", name = "Example", url = "https://one.example")
        val changed = original.copy(url = "https://two.example")
        assertNotEquals(
            MerkleTreeHelper.buildMerkleVault(listOf(original)).rootHash,
            MerkleTreeHelper.buildMerkleVault(listOf(changed)).rootHash
        )
    }

    @Test
    fun fieldSeparatorsCannotCollideInItemHash() {
        val a = SyncItem(cxfItemId = "id-1", name = "a|b", username = "c")
        val b = SyncItem(cxfItemId = "id-1", name = "a", username = "b|c")
        assertNotEquals(MerkleTreeHelper.computeItemHash(a), MerkleTreeHelper.computeItemHash(b))
    }

    @Test
    fun clearingAFieldIsRetainedByResolution() {
        val local = SyncItem("id-1", "Example", password = "old", modifiedAt = 10)
        val remote = local.copy(password = "", modifiedAt = 20)
        val conflict = SyncConflictResolver.analyzeConflicts(listOf(local), listOf(remote)).single()
        assertEquals("", conflict.selectedPassword)
    }

    @Test
    fun cxfRecordsDoNotMatchPasswordsByDisplayName() {
        val password = SyncItem("password-1", "Example", username = "alice")
        val cxf = SyncItem("cxf-1", "Example", username = "alice", kind = "CXF_IMPORT")
        val conflicts = SyncConflictResolver.analyzeConflicts(listOf(password), listOf(cxf))
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.all { it.type == ConflictType.NEW_ENTRY_ADDED })
    }

    @Test
    fun similarDisplayNamesWithDifferentIdsRemainSeparate() {
        val a = SyncItem("one", "Example", username = "alice")
        val b = SyncItem("two", "Example", username = "alice")
        assertEquals(2, SyncConflictResolver.analyzeConflicts(listOf(a), listOf(b)).size)
    }

    @Test
    fun duplicateIdentityCannotDisappearFromMerkleRoot() {
        val a = SyncItem("same", "First")
        val b = SyncItem("same", "Second")
        assertFailsWith<IllegalArgumentException> {
            MerkleTreeHelper.buildMerkleVault(listOf(a, b))
        }
    }

    @Test
    fun olderProtocolPayloadIsRejectedBeforeApplyingRecords() {
        val legacy = syncJson.encodeToString(SyncPayload(items = emptyList(), version = 3))
        assertFailsWith<IllegalArgumentException> { parseSyncPayload(legacy) }
        val current = syncJson.encodeToString(SyncPayload(items = emptyList()))
        assertEquals(4, parseSyncPayload(current).version)
    }

    @Test
    fun permanentDeletionPayloadContainsOnlyIdentityAndDeletionTime() {
        val tombstone = SyncItem(
            cxfItemId = "shared-id", name = "", modifiedAt = 1_700_000_000_000,
            isDeleted = true, isPermanentDeletion = true
        )
        val payload = parseSyncPayload(syncJson.encodeToString(SyncPayload(items = listOf(tombstone))))
        assertTrue(payload.items.single().isPermanentDeletion)

        val malformed = tombstone.copy(name = "leaked name", password = "leaked secret")
        assertFailsWith<IllegalArgumentException> {
            parseSyncPayload(syncJson.encodeToString(SyncPayload(items = listOf(malformed))))
        }
    }
}
