package fi.iki.ede.safe.ui.sync

import korlibs.crypto.SHA256
import korlibs.crypto.encoding.Hex
import kotlinx.serialization.Serializable

@Serializable
data class MerkleItemDigest(
    val cxfItemId: String,
    val itemHash: String
)

@Serializable
data class MerkleCategoryDigest(
    val categoryName: String,
    val categoryHash: String,
    val itemDigests: Map<String, String> = emptyMap() // cxfItemId -> itemHash
)

@Serializable
data class MerkleVaultDigest(
    val rootHash: String,
    val categories: Map<String, MerkleCategoryDigest> = emptyMap() // categoryName -> MerkleCategoryDigest
)

object MerkleTreeHelper {

    /**
     * Computes deterministic SHA-256 leaf hash for an individual sync item.
     */
    fun computeItemHash(item: SyncItem): String {
        val raw = buildString {
            append(item.cxfItemId.trim())
            append("|")
            append(item.name.trim())
            append("|")
            append(item.username.trim().lowercase())
            append("|")
            append(item.password)
            append("|")
            append(item.note)
            append("|")
            append(item.categoryName.trim())
            append("|")
            append(item.modifiedAt)
            append("|")
            append(item.isDeleted)
        }
        val digest = SHA256.digest(raw.encodeToByteArray())
        return Hex.encode(digest.bytes)
    }

    /**
     * Builds a full 3-level hierarchical Merkle Tree representation of the vault:
     * Level 0 (Leaves): Item Hashes
     * Level 1 (Branches): Category Hashes
     * Level 2 (Root): Vault Root Hash
     */
    fun buildMerkleVault(items: List<SyncItem>): MerkleVaultDigest {
        val itemsByCategory = items.groupBy { it.categoryName.ifBlank { "General Vault" } }
        val categoryMap = mutableMapOf<String, MerkleCategoryDigest>()

        for ((catName, catItems) in itemsByCategory) {
            val itemDigests = catItems.associate { item ->
                item.cxfItemId to computeItemHash(item)
            }
            // Sort items deterministically by cxfItemId before hashing category node
            val sortedItemHashes = itemDigests.entries
                .sortedBy { it.key }
                .map { "${it.key}:${it.value}" }
                .joinToString(";")

            val catRaw = "$catName|$sortedItemHashes"
            val catHash = Hex.encode(SHA256.digest(catRaw.encodeToByteArray()).bytes)

            categoryMap[catName] = MerkleCategoryDigest(
                categoryName = catName,
                categoryHash = catHash,
                itemDigests = itemDigests
            )
        }

        // Compute Vault Root Hash from sorted categories
        val sortedCatHashes = categoryMap.entries
            .sortedBy { it.key }
            .map { "${it.key}:${it.value.categoryHash}" }
            .joinToString("||")

        val rootHash = Hex.encode(SHA256.digest(sortedCatHashes.encodeToByteArray()).bytes)

        return MerkleVaultDigest(
            rootHash = rootHash,
            categories = categoryMap
        )
    }

    /**
     * Compares two Merkle Vaults and pinpoints exactly which categories differ.
     * Returns an empty set if vaults are 100% identical.
     */
    fun findMismatchedCategories(
        localVault: MerkleVaultDigest,
        remoteVault: MerkleVaultDigest
    ): Set<String> {
        if (localVault.rootHash == remoteVault.rootHash) {
            return emptySet() // Exact bit-level parity! Fast path in O(1).
        }

        val allCategoryNames = (localVault.categories.keys + remoteVault.categories.keys).toSet()
        val mismatched = mutableSetOf<String>()

        for (cat in allCategoryNames) {
            val localCat = localVault.categories[cat]
            val remoteCat = remoteVault.categories[cat]
            if (localCat == null || remoteCat == null || localCat.categoryHash != remoteCat.categoryHash) {
                mismatched.add(cat)
            }
        }
        return mismatched
    }

    /**
     * Given two category digests, pinpoints the exact cxfItemId leaf nodes that diverge.
     */
    fun findMismatchedItemIds(
        localCat: MerkleCategoryDigest?,
        remoteCat: MerkleCategoryDigest?
    ): Set<String> {
        val localDigests = localCat?.itemDigests ?: emptyMap()
        val remoteDigests = remoteCat?.itemDigests ?: emptyMap()
        val allIds = (localDigests.keys + remoteDigests.keys).toSet()

        val divergentIds = mutableSetOf<String>()
        for (id in allIds) {
            val localH = localDigests[id]
            val remoteH = remoteDigests[id]
            if (localH == null || remoteH == null || localH != remoteH) {
                divergentIds.add(id)
            }
        }
        return divergentIds
    }
}
