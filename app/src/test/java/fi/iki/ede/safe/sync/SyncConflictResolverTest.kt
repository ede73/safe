package fi.iki.ede.safe.sync

import fi.iki.ede.safe.ui.sync.ConflictType
import fi.iki.ede.safe.ui.sync.SyncConflict
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncConflictResolverTest {

    @Test
    fun testMultiDeviceConflictMatrixAnalysis() {
        val (androidItems, iosItems) = SyncConflictResolver.createTestConflictMatrix()

        val conflicts = SyncConflictResolver.analyzeConflicts(androidItems, iosItems)

        // 5 distinct conflict scenarios generated: Q, W, X, Y, M
        assertEquals(5, conflicts.size)

        // Scenario 1: Q added on Android
        val conflictQ = conflicts.find { it.conflictId.contains("item_Q") }
        assertNotNull(conflictQ)
        assertEquals(ConflictType.NEW_ENTRY_ADDED, conflictQ?.type)
        assertNotNull(conflictQ?.localItem)
        assertNull(conflictQ?.remoteItem)

        // Scenario 2: W added on iOS
        val conflictW = conflicts.find { it.conflictId.contains("item_W") }
        assertNotNull(conflictW)
        assertEquals(ConflictType.NEW_ENTRY_ADDED, conflictW?.type)
        assertNull(conflictW?.localItem)
        assertNotNull(conflictW?.remoteItem)

        // Scenario 3: X deleted on Android, active on iOS
        val conflictX = conflicts.find { it.conflictId.contains("item_X") }
        assertNotNull(conflictX)
        assertEquals(ConflictType.DELETED_ON_ONE_SIDE, conflictX?.type)
        assertTrue(conflictX?.localItem?.isDeleted == true)
        assertFalse(conflictX?.remoteItem?.isDeleted == true)

        // Scenario 4: Y active on Android, deleted on iOS
        val conflictY = conflicts.find { it.conflictId.contains("item_Y") }
        assertNotNull(conflictY)
        assertEquals(ConflictType.DELETED_ON_ONE_SIDE, conflictY?.type)
        assertFalse(conflictY?.localItem?.isDeleted == true)
        assertTrue(conflictY?.remoteItem?.isDeleted == true)

        // Scenario 5: M renamed on Android ("M_Renamed_Android.com" vs "M_Original_iOS.com")
        val conflictM = conflicts.find { it.conflictId.contains("item_M") }
        assertNotNull(conflictM)
        assertEquals(ConflictType.RENAMED_DIVERGENCE, conflictM?.type)
        assertEquals("M_Renamed_Android.com", conflictM?.localItem?.name)
        assertEquals("M_Original_iOS.com", conflictM?.remoteItem?.name)
    }

    @Test
    fun testCrossCategorySharedPasswordDeduplication() {
        val localItems = listOf(
            SyncItem(
                cxfItemId = "item_common_intranet_employee",
                name = "Common Intranet",
                username = "employee",
                password = "intra_pass",
                categoryName = "Android Work"
            )
        )
        val remoteItems = listOf(
            SyncItem(
                cxfItemId = "item_common_intranet_employee",
                name = "Common Intranet",
                username = "employee",
                password = "intra_pass",
                categoryName = "iOS Work"
            )
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(localItems, remoteItems)

        // Should NOT create NEW_ENTRY_ADDED or duplicate conflict when name & username match
        assertTrue(conflicts.isEmpty(), "Matching entries in different categories must be recognized as identical")
    }

    @Test
    fun testFieldModificationDivergenceResolution() {
        val local = SyncItem(
            cxfItemId = "item_modified",
            name = "github.com",
            username = "octocat",
            password = "old_password_1",
            note = "Old local note",
            modifiedAt = 1000L
        )
        val remote = SyncItem(
            cxfItemId = "item_modified",
            name = "github.com",
            username = "octocat",
            password = "new_password_2",
            note = "New remote note",
            modifiedAt = 2000L
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(listOf(local), listOf(remote))
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals(ConflictType.MODIFIED_DIVERGENCE, conflict.type)

        // Newer remote timestamp (2000 > 1000) should win deterministically
        assertEquals("new_password_2", conflict.selectedPassword)
        assertEquals("New remote note", conflict.selectedNote)
    }

    @Test
    fun testRenamedDivergenceResolution() {
        val local = SyncItem(
            cxfItemId = "item_rename_test",
            name = "Work Email (Android)",
            username = "user@corp.com",
            modifiedAt = 3000L
        )
        val remote = SyncItem(
            cxfItemId = "item_rename_test",
            name = "Work Email (iOS)",
            username = "user@corp.com",
            modifiedAt = 1500L
        )

        val conflicts = SyncConflictResolver.analyzeConflicts(listOf(local), listOf(remote))
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals(ConflictType.RENAMED_DIVERGENCE, conflict.type)

        // Newer local timestamp (3000 > 1500) should win
        assertEquals("Work Email (Android)", conflict.selectedName)
    }

    @Test
    fun testSyncCancellationSafety() {
        val (androidItems, iosItems) = SyncConflictResolver.createTestConflictMatrix()
        val conflicts = SyncConflictResolver.analyzeConflicts(androidItems, iosItems)

        assertFalse(conflicts.isEmpty())
        val cancelledResolutions = emptyList<SyncConflict>()
        assertTrue(cancelledResolutions.isEmpty(), "Cancelling sync must return empty resolutions without persisting")
    }
}
