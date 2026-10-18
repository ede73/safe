@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.desktop

import fi.iki.ede.safe.ui.sync.ConflictType
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncItem
import fi.iki.ede.safe.ui.sync.SyncPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThreeDeviceConvergenceUnitTest {

    private fun simulatePeerSync(
        localItems: List<SyncItem>,
        remoteItems: List<SyncItem>
    ): Pair<List<SyncItem>, List<SyncItem>> {
        val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remoteItems)
        val resolvedMap = mutableMapOf<String, SyncItem>()

        for (c in conflicts) {
            val key = c.conflictId
            val name = c.selectedName.ifBlank { c.localItem?.name ?: c.remoteItem?.name ?: "" }
            val user = c.selectedUsername.ifBlank { c.localItem?.username ?: c.remoteItem?.username ?: "" }
            val pass = c.selectedPassword.ifBlank { c.localItem?.password ?: c.remoteItem?.password ?: "" }
            val note = c.selectedNote.ifBlank { c.localItem?.note ?: c.remoteItem?.note ?: "" }
            val url = c.selectedUrl.ifBlank { c.localItem?.url ?: c.remoteItem?.url ?: "" }
            val isDel = c.selectedIsDeleted
            val modTime = maxOf(c.localItem?.modifiedAt ?: 0L, c.remoteItem?.modifiedAt ?: 0L)
            val catName = c.localItem?.categoryName?.ifBlank { null }
                ?: c.remoteItem?.categoryName?.ifBlank { null } ?: "General Vault"

            resolvedMap[key] = SyncItem(
                cxfItemId = key,
                name = name,
                username = user,
                password = pass,
                note = note,
                url = url,
                categoryName = catName,
                isDeleted = isDel,
                modifiedAt = modTime
            )
        }

        // Add any items that were identical on both sides (no conflict generated)
        for (item in localItems) {
            if (!resolvedMap.containsKey(item.cxfItemId)) {
                resolvedMap[item.cxfItemId] = item
            }
        }
        for (item in remoteItems) {
            if (!resolvedMap.containsKey(item.cxfItemId)) {
                resolvedMap[item.cxfItemId] = item
            }
        }
        val mergedList = resolvedMap.values.toList()
        return Pair(mergedList, mergedList)
    }

    @Test
    fun testThreeWayFullConflictMatrixConvergence() {
        val now = 1789356600000L

        // Device A (Android)
        val itemsA = listOf(
            SyncItem(cxfItemId = "shared_root", name = "Shared Root Login", username = "shared_user", password = "shared_pass_123", categoryName = "General Vault", modifiedAt = now),
            SyncItem(cxfItemId = "shared_intra", name = "Common Intranet", username = "employee", password = "intra_pass", categoryName = "Android Work", modifiedAt = now),
            SyncItem(cxfItemId = "shared_bank", name = "Common Banking", username = "fin_user", password = "bank_pass", categoryName = "Android Finance", modifiedAt = now),
            SyncItem(cxfItemId = "shared_cloud", name = "Common Storage", username = "cloud_user", password = "cloud_pass", categoryName = "Android Cloud", modifiedAt = now),
            SyncItem(cxfItemId = "added_android", name = "Android Unique App", username = "droid_user", password = "droid_pass", categoryName = "Android Work", modifiedAt = now),
            SyncItem(cxfItemId = "item_deleted_on_a", name = "Old Service (Deleted on Android)", username = "old_user", password = "old_pass", isDeleted = true, categoryName = "General Vault", modifiedAt = now)
        )

        // Device B (iOS)
        val itemsB = listOf(
            SyncItem(cxfItemId = "shared_root", name = "Shared Root Login", username = "shared_user", password = "shared_pass_123", categoryName = "General Vault", modifiedAt = now),
            SyncItem(cxfItemId = "shared_intra", name = "Common Intranet", username = "employee", password = "intra_pass", categoryName = "iOS Work", modifiedAt = now),
            SyncItem(cxfItemId = "shared_bank", name = "Common Banking", username = "fin_user", password = "bank_pass", categoryName = "iOS Finance", modifiedAt = now),
            SyncItem(cxfItemId = "shared_cloud", name = "Common Storage", username = "cloud_user", password = "cloud_pass", categoryName = "iOS Cloud", modifiedAt = now),
            SyncItem(cxfItemId = "added_ios", name = "iOS Unique App", username = "ios_user", password = "ios_pass", categoryName = "iOS Work", modifiedAt = now),
            SyncItem(cxfItemId = "item_deleted_on_a", name = "Old Service (Deleted on Android)", username = "old_user", password = "old_pass", isDeleted = false, categoryName = "General Vault", modifiedAt = now - 1000)
        )

        // Device C (Desktop)
        val itemsC = listOf(
            SyncItem(cxfItemId = "shared_root", name = "Shared Root Login", username = "shared_user", password = "shared_pass_123", categoryName = "General Vault", modifiedAt = now),
            SyncItem(cxfItemId = "shared_intra", name = "Common Intranet", username = "employee", password = "intra_pass", categoryName = "Desktop Work", modifiedAt = now),
            SyncItem(cxfItemId = "shared_bank", name = "Common Banking", username = "fin_user", password = "bank_pass", categoryName = "Desktop Finance", modifiedAt = now),
            SyncItem(cxfItemId = "shared_cloud", name = "Common Storage", username = "cloud_user", password = "cloud_pass", categoryName = "Desktop Cloud", modifiedAt = now),
            SyncItem(cxfItemId = "added_desktop", name = "Desktop Unique App", username = "desk_user", password = "desk_pass", categoryName = "Desktop Work", modifiedAt = now)
        )

        // Step 1: Sync A <-> B
        val (afterSync1A, afterSync1B) = simulatePeerSync(itemsA, itemsB)
        assertEquals(afterSync1A.size, afterSync1B.size)

        // Step 2: Sync A <-> C
        val (afterSync2A, afterSync2C) = simulatePeerSync(afterSync1A, itemsC)
        assertEquals(afterSync2A.size, afterSync2C.size)

        // Step 3: Sync C <-> B
        val (finalC, finalB) = simulatePeerSync(afterSync2C, afterSync1B)
        val (finalA, _) = simulatePeerSync(afterSync2A, finalC)

        // All 3 peers must have exact same number of entries: 4 shared + 3 unique + 1 deleted = 8 items
        assertEquals(8, finalA.size)
        assertEquals(8, finalB.size)
        assertEquals(8, finalC.size)

        val keysA = finalA.map { it.cxfItemId }.sorted()
        val keysB = finalB.map { it.cxfItemId }.sorted()
        val keysC = finalC.map { it.cxfItemId }.sorted()

        assertEquals(keysA, keysB, "Android and iOS keys must match exactly")
        assertEquals(keysA, keysC, "Android and Desktop keys must match exactly")
    }

    @Test
    fun testExplicitDeletionPropagatesAcrossConvergedCluster() {
        val now = 1789356600000L

        // Initial converged state: 4 active items shared across Devices A, B, and C
        val baseItems = listOf(
            SyncItem(cxfItemId = "item_1", name = "Bank 1", username = "user1", password = "pass1", modifiedAt = now),
            SyncItem(cxfItemId = "item_2", name = "Social 2", username = "user2", password = "pass2", modifiedAt = now),
            SyncItem(cxfItemId = "item_3", name = "Email 3", username = "user3", password = "pass3", modifiedAt = now),
            SyncItem(cxfItemId = "item_4", name = "Work 4", username = "user4", password = "pass4", modifiedAt = now)
        )

        // User on Device A explicitly deletes item_3 at now + 5000ms
        val itemsA = baseItems.map {
            if (it.cxfItemId == "item_3") it.copy(isDeleted = true, modifiedAt = now + 5000L) else it
        }
        val itemsB = baseItems
        val itemsC = baseItems

        // Step 1: Device A syncs with Device B
        val (afterAB_A, afterAB_B) = simulatePeerSync(itemsA, itemsB)
        // Verify item_3 is soft-deleted on Device B
        val item3OnB = afterAB_B.find { it.cxfItemId == "item_3" }
        assertTrue(item3OnB != null && item3OnB.isDeleted, "Item 3 must be soft-deleted on Device B")

        // Step 2: Device A syncs with Device C
        val (afterAC_A, afterAC_C) = simulatePeerSync(afterAB_A, itemsC)
        val item3OnC = afterAC_C.find { it.cxfItemId == "item_3" }
        assertTrue(item3OnC != null && item3OnC.isDeleted, "Item 3 must be soft-deleted on Device C")

        // Step 3: Device B syncs with Device C
        val (finalB, finalC) = simulatePeerSync(afterAB_B, afterAC_C)
        val (finalA, _) = simulatePeerSync(afterAC_A, finalB)

        // Assert: On all 3 devices, exactly 3 active items remain and item_3 is deleted in trash
        assertEquals(3, finalA.count { !it.isDeleted }, "Device A must have exactly 3 active items")
        assertEquals(3, finalB.count { !it.isDeleted }, "Device B must have exactly 3 active items")
        assertEquals(3, finalC.count { !it.isDeleted }, "Device C must have exactly 3 active items")

        assertTrue(finalA.first { it.cxfItemId == "item_3" }.isDeleted, "Device A retains item_3 as deleted")
        assertTrue(finalB.first { it.cxfItemId == "item_3" }.isDeleted, "Device B retains item_3 as deleted")
        assertTrue(finalC.first { it.cxfItemId == "item_3" }.isDeleted, "Device C retains item_3 as deleted")
    }

    @Test
    fun testLongDisparityConcurrentEditWinsOverOlderDeletion() {
        val now = 1789356600000L

        // Device A deleted item at T = 2000
        val itemsA = listOf(
            SyncItem(cxfItemId = "cred_vpn", name = "Corp VPN", username = "alice", password = "old_pass", isDeleted = true, modifiedAt = now + 2000L)
        )

        // Device B was offline and updated password to "brand_new_pass_2026" at T = 5000 (after deletion)
        val itemsB = listOf(
            SyncItem(cxfItemId = "cred_vpn", name = "Corp VPN", username = "alice", password = "brand_new_pass_2026", isDeleted = false, modifiedAt = now + 5000L)
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(itemsA, itemsB)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()

        assertEquals(ConflictType.DELETED_ON_ONE_SIDE, conflict.type)
        assertEquals(false, conflict.selectedIsDeleted, "Concurrent edit made after deletion must win to prevent data loss")
        assertEquals("brand_new_pass_2026", conflict.selectedPassword, "Winning password must be the newest remote password")
    }

    @Test
    fun testLongDisparityNewerDeletionWinsOverOlderEdit() {
        val now = 1789356600000L

        // Device A deleted item at T = 5000
        val itemsA = listOf(
            SyncItem(cxfItemId = "cred_legacy", name = "Legacy Forum", username = "bob", password = "pass", isDeleted = true, modifiedAt = now + 5000L)
        )

        // Device B was offline and last modified at T = 1000 (before deletion)
        val itemsB = listOf(
            SyncItem(cxfItemId = "cred_legacy", name = "Legacy Forum", username = "bob", password = "pass", isDeleted = false, modifiedAt = now + 1000L)
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(itemsA, itemsB)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()

        assertEquals(ConflictType.DELETED_ON_ONE_SIDE, conflict.type)
        assertEquals(true, conflict.selectedIsDeleted, "Deletion made after edit must win and propagate soft-deletion")
    }

    @Test
    fun testIncomingRemoteDeletedItemNeverResurrectedAsActive() {
        // Device A has purged the item completely from local DB
        val itemsA = emptyList<SyncItem>()

        // Device B still holds the tombstone in Trash
        val itemsB = listOf(
            SyncItem(cxfItemId = "purged_item", name = "Purged Service", username = "carol", password = "pwd", isDeleted = true, modifiedAt = 1000L)
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(itemsA, itemsB)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()

        assertEquals(ConflictType.NEW_ENTRY_ADDED, conflict.type)
        assertEquals(true, conflict.selectedIsDeleted, "Incoming deleted item from remote must remain deleted, never resurrected as active")
    }
}
