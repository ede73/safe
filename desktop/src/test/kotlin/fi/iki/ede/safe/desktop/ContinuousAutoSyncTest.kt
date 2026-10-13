package fi.iki.ede.safe.desktop

import fi.iki.ede.safe.ui.sync.ContinuousAutoSyncCoordinator
import fi.iki.ede.safe.ui.sync.DiscoveredDevice
import fi.iki.ede.safe.ui.sync.MerkleTreeHelper
import fi.iki.ede.safe.ui.sync.SyncConflictResolver
import fi.iki.ede.safe.ui.sync.SyncItem
import fi.iki.ede.safe.ui.sync.SyncNetworkService
import fi.iki.ede.safe.ui.sync.SyncPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContinuousAutoSyncTest {

    private val baseTimestamp = 1789356600000L

    private fun createInitialDataset(): List<SyncItem> {
        return listOf(
            SyncItem(cxfItemId = "item_slack", name = "Slack", username = "user_w", password = "pass_slack", categoryName = "Work", modifiedAt = baseTimestamp),
            SyncItem(cxfItemId = "item_jira", name = "Jira", username = "user_w", password = "pass_jira", categoryName = "Work", modifiedAt = baseTimestamp),
            SyncItem(cxfItemId = "item_paypal", name = "PayPal", username = "user_f", password = "pass_paypal_v1", categoryName = "Finance", modifiedAt = baseTimestamp),
            SyncItem(cxfItemId = "item_revolut", name = "Revolut", username = "user_f", password = "pass_rev", categoryName = "Finance", modifiedAt = baseTimestamp),
            SyncItem(cxfItemId = "item_netflix", name = "Netflix", username = "user_p", password = "pass_netflix_v1", categoryName = "Personal", modifiedAt = baseTimestamp)
        )
    }

    @Test
    fun testContinuousSyncConvergesUponMutationAndEnjoysFastPathNoOp() {
        val datasetA = createInitialDataset().toMutableList()
        val datasetB = createInitialDataset().toMutableList()

        // 1. Initial State: Both peers start in 100% Merkle parity
        val treeA_initial = MerkleTreeHelper.buildMerkleVault(datasetA)
        val treeB_initial = MerkleTreeHelper.buildMerkleVault(datasetB)
        assertEquals(treeA_initial.rootHash, treeB_initial.rootHash, "Initial Merkle roots must match")

        // 2. Mutation on Peer A: User modifies PayPal password and note
        val mutationTimestamp = baseTimestamp + 5000L
        val mutatedIndex = datasetA.indexOfFirst { it.cxfItemId == "item_paypal" }
        datasetA[mutatedIndex] = datasetA[mutatedIndex].copy(
            password = "pass_paypal_v2_new",
            note = "Updated via Phone",
            modifiedAt = mutationTimestamp
        )

        // 3. Merkle roots now diverge
        val treeA_mutated = MerkleTreeHelper.buildMerkleVault(datasetA)
        assertNotEquals(treeA_mutated.rootHash, treeB_initial.rootHash, "Merkle roots must diverge after local mutation")

        // 4. Verify Merkle tree prunes divergence down to only the Finance category and item_paypal
        val mismatchedCats = MerkleTreeHelper.findMismatchedCategories(treeA_mutated, treeB_initial)
        assertEquals(setOf("Finance"), mismatchedCats, "Only Finance category should be flagged as mismatched")

        val mismatchedItems = MerkleTreeHelper.findMismatchedItemIds(
            treeA_mutated.categories["Finance"],
            treeB_initial.categories["Finance"]
        )
        assertEquals(setOf("item_paypal"), mismatchedItems, "Only item_paypal should diverge")

        // 5. Continuous Sync Trigger: Peer B receives Peer A's delta and reconciles
        val conflictsOnB = SyncConflictResolver.analyzeConflicts(datasetB, datasetA)
        val reconciledB = datasetB.map { bItem ->
            val matchingConflict = conflictsOnB.find { it.conflictId == bItem.cxfItemId }
            if (matchingConflict != null) {
                bItem.copy(
                    name = matchingConflict.selectedName,
                    username = matchingConflict.selectedUsername,
                    password = matchingConflict.selectedPassword,
                    note = matchingConflict.selectedNote,
                    modifiedAt = maxOf(matchingConflict.localItem?.modifiedAt ?: 0L, matchingConflict.remoteItem?.modifiedAt ?: 0L),
                    isDeleted = matchingConflict.selectedIsDeleted
                )
            } else bItem
        }

        // 6. After sync: Peer B's Merkle root now matches Peer A's root!
        val treeB_reconciled = MerkleTreeHelper.buildMerkleVault(reconciledB)
        assertEquals(treeA_mutated.rootHash, treeB_reconciled.rootHash, "Peer B must reach exact Merkle root parity with Peer A")

        // 7. Fast-Path Verification: Subsequent heartbeat checks see identical roots -> No-op!
        val mismatchedAfter = MerkleTreeHelper.findMismatchedCategories(treeA_mutated, treeB_reconciled)
        assertTrue(mismatchedAfter.isEmpty(), "Subsequent heartbeat checks must return empty mismatches (O(1) fast path)")

        // 8. Assert zero data loss on both sides
        assertEquals(5, datasetA.size)
        assertEquals(5, reconciledB.size)
        assertEquals("pass_paypal_v2_new", reconciledB.find { it.cxfItemId == "item_paypal" }?.password)
        assertEquals("Updated via Phone", reconciledB.find { it.cxfItemId == "item_paypal" }?.note)
    }

    @Test
    fun testConcurrentAdditionsConvergeToIdenticalMerkleRoot() {
        val base = createInitialDataset()
        // Peer A adds Spotify in Personal
        val datasetA = base + SyncItem(cxfItemId = "item_spotify", name = "Spotify", categoryName = "Personal", modifiedAt = baseTimestamp + 1000)
        // Peer B adds Notion in Work
        val datasetB = base + SyncItem(cxfItemId = "item_notion", name = "Notion", categoryName = "Work", modifiedAt = baseTimestamp + 2000)

        // Both Merkle roots differ from each other
        val treeA = MerkleTreeHelper.buildMerkleVault(datasetA)
        val treeB = MerkleTreeHelper.buildMerkleVault(datasetB)
        assertNotEquals(treeA.rootHash, treeB.rootHash)

        // Two-way sync exchange
        val conflictsOnB = SyncConflictResolver.analyzeConflicts(datasetB, datasetA)
        val mergedOnB = datasetB + conflictsOnB.filter { it.localItem == null }.map { it.remoteItem!! }

        val conflictsOnA = SyncConflictResolver.analyzeConflicts(datasetA, mergedOnB)
        val mergedOnA = datasetA + conflictsOnA.filter { it.localItem == null }.map { it.remoteItem!! }

        val finalTreeA = MerkleTreeHelper.buildMerkleVault(mergedOnA)
        val finalTreeB = MerkleTreeHelper.buildMerkleVault(mergedOnB)

        // Both peers now converge to the EXACT same Merkle Root Hash!
        assertEquals(finalTreeA.rootHash, finalTreeB.rootHash, "Concurrent additions must converge to the exact same Merkle Root Hash")
        assertEquals(7, mergedOnA.size)
        assertEquals(7, mergedOnB.size)
    }

    @Test
    fun testAddCredentialInWorkCategoryOnIosPropagatesToAndroidAndDesktop() {
        val initialVault = createInitialDataset()
        val iosVault = initialVault.toMutableList()
        val androidVault = initialVault.toMutableList()
        val desktopVault = initialVault.toMutableList()

        // 1. User adds "Figma" under "Work" category on iOS
        val figma = SyncItem(
            cxfItemId = "item_figma",
            name = "Figma",
            username = "designer@company.com",
            password = "secure_figma_pwd_2026",
            categoryName = "Work",
            url = "https://figma.com",
            modifiedAt = baseTimestamp + 3000L
        )
        iosVault.add(figma)

        // Merkle root on iOS now reflects the new Work entry
        val iosTree = MerkleTreeHelper.buildMerkleVault(iosVault)
        val androidTreeInitial = MerkleTreeHelper.buildMerkleVault(androidVault)
        assertNotEquals(iosTree.rootHash, androidTreeInitial.rootHash)

        // 2. Android receives sync payload from iOS
        val conflictsOnAndroid = SyncConflictResolver.analyzeConflicts(androidVault, iosVault)
        assertEquals(1, conflictsOnAndroid.size)
        assertEquals("item_figma", conflictsOnAndroid[0].conflictId)
        assertEquals("Figma", conflictsOnAndroid[0].selectedName)
        assertEquals("Work", conflictsOnAndroid[0].remoteItem?.categoryName)

        // Apply to Android vault
        androidVault.add(conflictsOnAndroid[0].remoteItem!!)
        val androidTreeAfter = MerkleTreeHelper.buildMerkleVault(androidVault)
        assertEquals(iosTree.rootHash, androidTreeAfter.rootHash, "Android must reach Merkle parity with iOS")

        // 3. Desktop receives sync payload from iOS
        val conflictsOnDesktop = SyncConflictResolver.analyzeConflicts(desktopVault, iosVault)
        assertEquals(1, conflictsOnDesktop.size)
        desktopVault.add(conflictsOnDesktop[0].remoteItem!!)
        val desktopTreeAfter = MerkleTreeHelper.buildMerkleVault(desktopVault)
        assertEquals(iosTree.rootHash, desktopTreeAfter.rootHash, "Desktop must reach Merkle parity with iOS")

        // 4. All three devices converged
        assertEquals(6, iosVault.size)
        assertEquals(6, androidVault.size)
        assertEquals(6, desktopVault.size)
        assertEquals(iosTree.rootHash, androidTreeAfter.rootHash)
        assertEquals(androidTreeAfter.rootHash, desktopTreeAfter.rootHash)
    }
}
