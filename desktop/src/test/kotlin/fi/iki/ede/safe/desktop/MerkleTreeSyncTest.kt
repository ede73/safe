package fi.iki.ede.safe.desktop

import fi.iki.ede.safe.ui.sync.MerkleTreeHelper
import fi.iki.ede.safe.ui.sync.SyncItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerkleTreeSyncTest {

    private fun createSampleVault(): List<SyncItem> {
        val now = 1789356600000L
        return listOf(
            // Work
            SyncItem(cxfItemId = "item_slack", name = "Slack", username = "u1", password = "p1", categoryName = "Work", modifiedAt = now),
            SyncItem(cxfItemId = "item_jira", name = "Jira", username = "u2", password = "p2", categoryName = "Work", modifiedAt = now),
            // Personal
            SyncItem(cxfItemId = "item_netflix", name = "Netflix", username = "u3", password = "p3", categoryName = "Personal", modifiedAt = now),
            SyncItem(cxfItemId = "item_spotify", name = "Spotify", username = "u4", password = "p4", categoryName = "Personal", modifiedAt = now),
            // Finance
            SyncItem(cxfItemId = "item_paypal", name = "PayPal", username = "u5", password = "p5", categoryName = "Finance", modifiedAt = now)
        )
    }

    @Test
    fun testIdenticalVaultsHaveIdenticalMerkleRoot() {
        val vaultA = createSampleVault()
        val vaultB = createSampleVault()

        val treeA = MerkleTreeHelper.buildMerkleVault(vaultA)
        val treeB = MerkleTreeHelper.buildMerkleVault(vaultB)

        assertEquals(treeA.rootHash, treeB.rootHash, "Identical vaults must yield exact same root hash")
        val mismatched = MerkleTreeHelper.findMismatchedCategories(treeA, treeB)
        assertTrue(mismatched.isEmpty(), "Mismatched categories set must be empty for identical vaults")
    }

    @Test
    fun testSingleItemMutationIsolatesToExactCategoryAndItemId() {
        val vaultA = createSampleVault()
        // Modify ONLY Netflix password on B (in Personal category)
        val vaultB = vaultA.map { item ->
            if (item.cxfItemId == "item_netflix") {
                item.copy(password = "new_secret_password_2026", modifiedAt = item.modifiedAt + 1000)
            } else item
        }

        val treeA = MerkleTreeHelper.buildMerkleVault(vaultA)
        val treeB = MerkleTreeHelper.buildMerkleVault(vaultB)

        // Root hashes differ
        assertNotEquals(treeA.rootHash, treeB.rootHash, "Modified vault must produce different root hash")

        // Work and Finance hashes must still match!
        assertEquals(treeA.categories["Work"]?.categoryHash, treeB.categories["Work"]?.categoryHash)
        assertEquals(treeA.categories["Finance"]?.categoryHash, treeB.categories["Finance"]?.categoryHash)

        // Only Personal category diverges
        val mismatchedCats = MerkleTreeHelper.findMismatchedCategories(treeA, treeB)
        assertEquals(setOf("Personal"), mismatchedCats, "Only Personal category should be identified as divergent")

        // Pinpoint the exact item in Personal
        val mismatchedItems = MerkleTreeHelper.findMismatchedItemIds(
            treeA.categories["Personal"],
            treeB.categories["Personal"]
        )
        assertEquals(setOf("item_netflix"), mismatchedItems, "Only item_netflix should be identified as divergent leaf")
    }

    @Test
    fun testAdditionOfNewCategoryDivergesCleanly() {
        val vaultA = createSampleVault()
        val vaultB = vaultA + SyncItem(
            cxfItemId = "item_github",
            name = "GitHub",
            username = "git_user",
            password = "octo_pass",
            categoryName = "Social",
            modifiedAt = 1789356600000L
        )

        val treeA = MerkleTreeHelper.buildMerkleVault(vaultA)
        val treeB = MerkleTreeHelper.buildMerkleVault(vaultB)

        val mismatchedCats = MerkleTreeHelper.findMismatchedCategories(treeA, treeB)
        assertEquals(setOf("Social"), mismatchedCats, "New Social category must be detected as mismatched")
    }
}
