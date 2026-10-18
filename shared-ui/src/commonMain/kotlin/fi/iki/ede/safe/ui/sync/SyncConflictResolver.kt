package fi.iki.ede.safe.ui.sync

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.cryptoobjects.plainName
import fi.iki.ede.cryptoobjects.plainDescription
import fi.iki.ede.cryptoobjects.plainExtensions
import fi.iki.ede.cryptoobjects.plainNote
import fi.iki.ede.cryptoobjects.plainPassword
import fi.iki.ede.cryptoobjects.plainUsername
import fi.iki.ede.cryptoobjects.plainWebsite
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.db.cxf.CXFAccount
import fi.iki.ede.db.cxf.CXFImport
import fi.iki.ede.db.cxf.CXFPasskey
import fi.iki.ede.db.cxf.cachedDecryptedCxfItemId
import fi.iki.ede.db.cxf.cachedDecryptedCredentialId
import fi.iki.ede.db.cxf.cachedDecryptedEmail
import fi.iki.ede.db.cxf.cachedDecryptedName
import fi.iki.ede.db.cxf.cachedDecryptedNote
import fi.iki.ede.db.cxf.cachedDecryptedPassword
import fi.iki.ede.db.cxf.cachedDecryptedUrl
import fi.iki.ede.db.cxf.cachedDecryptedUserHandle
import fi.iki.ede.db.cxf.cachedDecryptedUsername
import fi.iki.ede.preferences.Preferences
import korlibs.crypto.HMAC
import korlibs.crypto.encoding.Hex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.time.ExperimentalTime

@Serializable
data class SyncItem(
    val cxfItemId: String,
    val name: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
    val categoryName: String = "",
    val modifiedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val kind: String = "PASSWORD",
    val cxfAccountId: String = "",
    val cxfAccountEmail: String = "",
    val cxfType: String = "",
    val rpId: String = "",
    val credentialId: String = "",
    val userHandle: String = "",
    val cxfHash: String = "",
    val sourceCxfItemId: String = "",
    val flaggedIgnored: Boolean = false,
    val createdAt: Long = 0L,
    /** The credential row was physically purged; this identity-only tombstone must propagate. */
    val isPermanentDeletion: Boolean = false
)

enum class ConflictType {
    NEW_ENTRY_ADDED,      // Entry exists on one device, missing on the other
    MODIFIED_DIVERGENCE,  // Both devices modified fields independently
    DELETED_ON_ONE_SIDE,  // Deleted on one device, active/modified on the other
    RENAMED_DIVERGENCE    // Renamed on one device, original or differently named on the other
}

private fun winningItem(local: SyncItem?, remote: SyncItem?): SyncItem? =
    listOfNotNull(local, remote).maxWithOrNull(
        compareBy<SyncItem> { it.modifiedAt }.thenBy { MerkleTreeHelper.computeItemHash(it) }
    )

@Serializable
data class SyncConflict(
    val conflictId: String,
    val type: ConflictType,
    val localItem: SyncItem?,
    val remoteItem: SyncItem?,
    var selectedName: String = winningItem(localItem, remoteItem)?.name ?: "",
    var selectedUsername: String = winningItem(localItem, remoteItem)?.username ?: "",
    var selectedPassword: String = winningItem(localItem, remoteItem)?.password ?: "",
    var selectedNote: String = winningItem(localItem, remoteItem)?.note ?: "",
    var selectedUrl: String = winningItem(localItem, remoteItem)?.url ?: "",
    var selectedCategoryName: String = winningItem(localItem, remoteItem)?.categoryName ?: "General Vault",
    var selectedIsDeleted: Boolean = localItem?.isDeleted ?: remoteItem?.isDeleted ?: false
)

/**
 * Multi-device conflict resolution engine for P2P CXF sync.
 *
 * DESIGN DECISION & SCALE ASSUMPTIONS:
 * 1. Scope & Conflict Volume: This interactive modal UI (`ConflictResolutionDialog`) is designed
 *    under the assumption of *minor conflicts* (typical day-to-day divergence of < 20-50 items).
 * 2. High-Divergence Handling (100–500+ Conflicts): For large-scale sync conflicts (e.g., 100 to 500+ divergent items),
 *    an in-memory modal dialog is insufficient. Resolving large batches requires a dedicated "parking" or
 *    staging area UI (similar to the GPM plugin management UI) where users can review and resolve items incrementally
 *    over multiple sessions.
 * 3. Cancellation Safety: Sync and conflict resolution are strictly opt-in and non-destructive. The user can
 *    ALWAYS cancel the sync operation at any time without committing changes to either local or remote storage.
 */
@OptIn(ExperimentalTime::class)
object SyncConflictResolver {

    /**
     * Compares local (e.g. Android) and remote (e.g. iOS) datasets and identifies all conflict scenarios:
     * - New entry added on Android (Q)
     * - New entry added on iOS (W)
     * - Deleted on Android (X)
     * - Deleted on iOS (Y)
     * - Renamed on Android (M)
     */
    fun analyzeConflicts(
        localItems: List<SyncItem>,
        remoteItems: List<SyncItem>
    ): List<SyncConflict> {
        val conflicts = mutableListOf<SyncConflict>()
        val matchedRemoteIndexes = mutableSetOf<Int>()
        val identityKey = Preferences.getVaultClusterSecret()?.let { runCatching { Hex.decode(it) }.getOrNull() }
        val localFallbackCounts = localItems.mapNotNull { fallbackContentIdentity(it, identityKey) }.groupingBy { it }.eachCount()
        val remoteFallbackCounts = remoteItems.mapNotNull { fallbackContentIdentity(it, identityKey) }.groupingBy { it }.eachCount()

        for ((localIndex, local) in localItems.withIndex()) {
            // Only a stable identity can justify updating an existing credential.
            // Similar names/usernames alone are unsafe. For old databases without
            // shared IDs, identical full password content can bootstrap one stable
            // identity only when it is unique on both sides.
            val exactRemoteIndex = remoteItems.indices.firstOrNull { index ->
                index !in matchedRemoteIndexes && local.kind == remoteItems[index].kind &&
                    local.cxfItemId.isNotBlank() && remoteItems[index].cxfItemId == local.cxfItemId
            }
            val fallbackIdentity = fallbackContentIdentity(local, identityKey)
            val fallbackRemoteIndex = if (exactRemoteIndex == null && local.kind == "PASSWORD" &&
                fallbackIdentity != null && localFallbackCounts[fallbackIdentity] == 1 &&
                remoteFallbackCounts[fallbackIdentity] == 1
            ) remoteItems.indices.firstOrNull { index ->
                index !in matchedRemoteIndexes && fallbackContentIdentity(remoteItems[index], identityKey) == fallbackIdentity
            } else null
            val matchedIndex = exactRemoteIndex ?: fallbackRemoteIndex
            val remoteMatch = matchedIndex?.let(remoteItems::get)
            val resolvedId = if (exactRemoteIndex != null) local.cxfItemId else fallbackIdentity?.let { "legacy-content:$it" }

            if (remoteMatch != null) {
                matchedRemoteIndexes.add(requireNotNull(matchedIndex))
                val stableId = requireNotNull(resolvedId)

                // Scenario 1: Deletion Conflict (Deleted on one side, active/modified on the other)
                if (local.isDeleted != remoteMatch.isDeleted) {
                    val deletionWins = if (local.isDeleted) {
                        local.modifiedAt >= remoteMatch.modifiedAt
                    } else {
                        remoteMatch.modifiedAt >= local.modifiedAt
                    }
                    conflicts.add(
                        SyncConflict(
                            conflictId = stableId,
                            type = ConflictType.DELETED_ON_ONE_SIDE,
                            localItem = local,
                            remoteItem = remoteMatch,
                            selectedIsDeleted = deletionWins
                        )
                    )
                }
                // Scenario 2: Rename Conflict (Names differ across devices for same item)
                else if (local.name != remoteMatch.name) {
                    conflicts.add(
                        SyncConflict(
                            conflictId = stableId,
                            type = ConflictType.RENAMED_DIVERGENCE,
                            localItem = local,
                            remoteItem = remoteMatch
                        )
                    )
                }
                // Scenario 3: Field Modifications Divergence
                else if (local != remoteMatch) {
                    conflicts.add(
                        SyncConflict(
                            conflictId = stableId,
                            type = ConflictType.MODIFIED_DIVERGENCE,
                            localItem = local,
                            remoteItem = remoteMatch
                        )
                    )
                }
            } else {
                // Scenario 4: Added on Local (Android Q) - Missing on Remote
                conflicts.add(
                    SyncConflict(
                        conflictId = local.cxfItemId.ifBlank { local.name },
                        type = ConflictType.NEW_ENTRY_ADDED,
                        localItem = local,
                        remoteItem = null,
                        selectedIsDeleted = local.isDeleted
                    )
                )
            }
        }

        // Scenario 5: Added on Remote (iOS W) - Missing on Local
        for ((remoteIndex, remote) in remoteItems.withIndex()) {
            if (remoteIndex !in matchedRemoteIndexes) {
                conflicts.add(
                    SyncConflict(
                        conflictId = remote.cxfItemId.ifBlank { remote.name },
                        type = ConflictType.NEW_ENTRY_ADDED,
                        localItem = null,
                        remoteItem = remote,
                        selectedIsDeleted = remote.isDeleted
                    )
                )
            }
        }

        return conflicts
    }

    /**
     * Keyed bootstrap identity for old password rows created independently on
     * multiple devices. It excludes local IDs, categories and metadata while
     * including complete password-entry content. Without the shared cluster key,
     * or when duplicates are ambiguous, bootstrap matching is disabled.
     */
    private fun fallbackContentIdentity(item: SyncItem, identityKey: ByteArray?): String? {
        if (identityKey == null) return null
        if (item.kind != "PASSWORD" || item.url.isBlank() && item.password.isBlank() ||
            item.name.isBlank() && item.url.isBlank()
        ) return null
        val canonicalContent = syncJson.encodeToString(
            item.copy(
                cxfItemId = "", categoryName = "", modifiedAt = 0L, isDeleted = false,
                cxfAccountId = "", cxfAccountEmail = "", cxfType = "", rpId = "",
                credentialId = "", userHandle = "", cxfHash = "", sourceCxfItemId = "",
                flaggedIgnored = false, createdAt = 0L
            )
        )
        val contentMac = HMAC.hmacSHA256(identityKey, canonicalContent.encodeToByteArray()).bytes
        return "legacy-content:${Hex.encode(contentMac)}"
    }

    private fun cleanDomain(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.contains("://")) s = s.substringAfter("://")
        return s.substringBefore("/").substringBefore("?").substringBefore(":")
    }

    /**
     * Creates a test dataset matrix covering all multi-device conflict scenarios:
     * - Q: Added on Android
     * - W: Added on iOS
     * - X: Deleted on Android
     * - Y: Deleted on iOS
     * - M: Renamed on Android ("M_Renamed_Android") vs iOS ("M_Original_iOS")
     */
    fun createTestConflictMatrix(): Pair<List<SyncItem>, List<SyncItem>> {
        val now = 1789356600000L

        val androidItems = listOf(
            SyncItem(cxfItemId = "item_Q", name = "Q_Android_Added.com", username = "user_q", password = "pass_q_android", modifiedAt = now),
            SyncItem(cxfItemId = "item_X", name = "X_Shared_Deleted_Android.com", username = "user_x", password = "pass_x", isDeleted = true, modifiedAt = now),
            SyncItem(cxfItemId = "item_Y", name = "Y_Shared_Active_Android.com", username = "user_y", password = "pass_y", isDeleted = false, modifiedAt = now - 1000),
            SyncItem(cxfItemId = "item_M", name = "M_Renamed_Android.com", username = "user_m", password = "pass_m_android", note = "Android note", modifiedAt = now)
        )

        val iosItems = listOf(
            SyncItem(cxfItemId = "item_W", name = "W_iOS_Added.com", username = "user_w", password = "pass_w_ios", modifiedAt = now),
            SyncItem(cxfItemId = "item_X", name = "X_Shared_Active_iOS.com", username = "user_x", password = "pass_x", isDeleted = false, modifiedAt = now - 1000),
            SyncItem(cxfItemId = "item_Y", name = "Y_Shared_Deleted_iOS.com", username = "user_y", password = "pass_y", isDeleted = true, modifiedAt = now),
            SyncItem(cxfItemId = "item_M", name = "M_Original_iOS.com", username = "user_m", password = "pass_m_ios", note = "iOS note", modifiedAt = now - 500)
        )

        return Pair(androidItems, iosItems)
    }

    /**
     * Seeds the local SQLite database (via DBHelperFactory.getDBHelper()) with either the Android
     * dataset or the iOS dataset for real matrix conflict testing.
     */
    fun seedLocalDatabase(isAndroid: Boolean) {
        val db = DBHelperFactory.getDBHelper()
        val categories = db.fetchAllCategoryRows()
        val testCat = categories.find { it.plainName == "Test Sync Matrix" }
        val catId = testCat?.id ?: run {
            val newCat = DecryptableCategoryEntry().apply {
                encryptedName = "Test Sync Matrix".encrypt()
            }
            db.addCategory(newCat)
        }

        val activeEntries = db.fetchAllRows(catId)
        val softDeletedEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllSoftDeleted() }
        val existingEntries = activeEntries + softDeletedEntries

        fun upsertEntry(cxfId: String, nameStr: String, userStr: String, passStr: String, noteStr: String = "", urlStr: String = "", isDel: Boolean = false) {
            val existing = existingEntries.find { entry ->
                entry.plainExtensions["cxfItemId"]?.contains(cxfId) == true ||
                        entry.plainUsername == userStr ||
                        (cleanDomain(entry.plainDescription) == cleanDomain(nameStr) && userStr.isNotBlank() && entry.plainUsername == userStr)
            }

            if (existing != null) {
                existing.description = nameStr.encrypt()
                existing.username = userStr.encrypt()
                existing.password = passStr.encrypt()
                existing.note = noteStr.encrypt()
                existing.website = urlStr.encrypt()
                existing.deleted = if (isDel) kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000 else 0L
                val extMap = existing.plainExtensions.toMutableMap()
                extMap["cxfItemId"] = setOf(cxfId)
                existing.extensions = existing.encryptExtension(extMap)
                db.updateSiteEntry(existing)
            } else {
                val entry = DecryptableSiteEntry(categoryId = catId).apply {
                    description = nameStr.encrypt()
                    username = userStr.encrypt()
                    password = passStr.encrypt()
                    note = noteStr.encrypt()
                    website = urlStr.encrypt()
                    deleted = if (isDel) kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000 else 0L
                    extensions = encryptExtension(mapOf("cxfItemId" to setOf(cxfId)))
                }
                db.addSiteEntry(entry)
            }
        }

        if (isAndroid) {
            // Android dataset: Q, X (deleted), Y (active), M (renamed)
            upsertEntry("item_Q", "Q_Android_Added.com", "user_q", "pass_q_android")
            upsertEntry("item_X", "X_Shared_Deleted_Android.com", "user_x", "pass_x", isDel = true)
            upsertEntry("item_Y", "Y_Shared_Active_Android.com", "user_y", "pass_y", isDel = false)
            upsertEntry("item_M", "M_Renamed_Android.com", "user_m", "pass_m_android", noteStr = "Android note")
        } else {
            // iOS dataset: W, X (active), Y (deleted), M (original)
            upsertEntry("item_W", "W_iOS_Added.com", "user_w", "pass_w_ios")
            upsertEntry("item_X", "X_Shared_Active_iOS.com", "user_x", "pass_x", isDel = false)
            upsertEntry("item_Y", "Y_Shared_Deleted_iOS.com", "user_y", "pass_y", isDel = true)
            upsertEntry("item_M", "M_Original_iOS.com", "user_m", "pass_m_ios", noteStr = "iOS note")
        }
    }

    /**
     * Reads all local database entries (active + soft-deleted) and converts them into SyncItems.
     */
    fun readLocalDatabaseAsSyncItems(): List<SyncItem> {
        val db = DBHelperFactory.getDBHelper()
        val categories = db.fetchAllCategoryRows()
        val categoryMap = categories.associateBy { it.id }
        val activeEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllActive() }
        val softDeletedEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllSoftDeleted() }
        val allEntries = activeEntries + softDeletedEntries
        val passwordItems = allEntries.map { entry ->
            val cxfIdFromExt = entry.plainExtensions["cxfItemId"]?.firstOrNull()
            val catName = categoryMap[entry.categoryId]?.plainName ?: "General Vault"
            // Backup/restore preserves the database ID for live entries. A rename,
            // username change, or duplicate display name must not change identity.
            val cxfId = cxfIdFromExt ?: "site-entry:${requireNotNull(entry.id)}"
            val delMillis = if (entry.deleted != 0L) {
                if (entry.deleted < 5_000_000_000L) entry.deleted * 1000L else entry.deleted
            } else 0L
            val modMillis = entry.passwordChangedDate?.toEpochMilliseconds() ?: 0L
            val effectiveTime = if (entry.deleted != 0L) maxOf(modMillis, delMillis) else modMillis
            SyncItem(
                cxfItemId = cxfId,
                name = entry.plainDescription,
                username = entry.plainUsername,
                password = entry.plainPassword,
                note = entry.plainNote,
                url = entry.plainWebsite,
                categoryName = catName,
                isDeleted = entry.deleted != 0L,
                modifiedAt = effectiveTime
            )
        }
        val permanentDeletionItems = kotlinx.coroutines.runBlocking {
            db.database.syncTombstoneDao().getAll().map { tombstone ->
                SyncItem(
                    cxfItemId = tombstone.cxfItemId,
                    name = "",
                    modifiedAt = tombstone.deletedAt * 1000L,
                    isDeleted = true,
                    isPermanentDeletion = true
                )
            }
        }
        val cxfItems = kotlinx.coroutines.runBlocking {
            val accounts = db.database.cxfAccountDao().getAll().associateBy { it.id }
            val accountItems = accounts.values.map { account ->
                SyncItem(
                    cxfItemId = "cxf-account:${account.cxfAccountId}",
                    name = account.cachedDecryptedEmail,
                    kind = "CXF_ACCOUNT",
                    cxfAccountId = account.cxfAccountId,
                    cxfAccountEmail = account.cachedDecryptedEmail
                )
            }
            val imports = db.database.cxfImportDao().getAll().mapNotNull { entry ->
                val account = accounts[entry.accountId] ?: return@mapNotNull null
                SyncItem(
                    cxfItemId = "cxf-import:${account.cxfAccountId}:${entry.cachedDecryptedCxfItemId}:${entry.hash}",
                    name = entry.cachedDecryptedName,
                    username = entry.cachedDecryptedUsername,
                    password = entry.cachedDecryptedPassword,
                    url = entry.cachedDecryptedUrl,
                    note = entry.cachedDecryptedNote,
                    categoryName = "CXF:${account.cxfAccountId}",
                    modifiedAt = entry.modifiedAt ?: entry.importedAt,
                    kind = "CXF_IMPORT",
                    cxfAccountId = account.cxfAccountId,
                    cxfAccountEmail = account.cachedDecryptedEmail,
                    cxfType = entry.type,
                    cxfHash = entry.hash,
                    sourceCxfItemId = entry.cachedDecryptedCxfItemId,
                    flaggedIgnored = entry.flaggedIgnored,
                    createdAt = entry.createdAt ?: 0L
                )
            }
            val passkeys = db.database.cxfPasskeyDao().getAll().mapNotNull { entry ->
                val account = accounts[entry.accountId] ?: return@mapNotNull null
                SyncItem(
                    cxfItemId = "cxf-passkey:${account.cxfAccountId}:${entry.cachedDecryptedCxfItemId}:${entry.hash}",
                    name = entry.cachedDecryptedName,
                    username = entry.cachedDecryptedUsername,
                    url = entry.cachedDecryptedUrl,
                    note = entry.cachedDecryptedNote,
                    categoryName = "CXF:${account.cxfAccountId}",
                    modifiedAt = entry.modifiedAt ?: entry.importedAt,
                    kind = "CXF_PASSKEY",
                    cxfAccountId = account.cxfAccountId,
                    cxfAccountEmail = account.cachedDecryptedEmail,
                    rpId = entry.rpId,
                    credentialId = entry.cachedDecryptedCredentialId,
                    userHandle = entry.cachedDecryptedUserHandle,
                    cxfHash = entry.hash,
                    sourceCxfItemId = entry.cachedDecryptedCxfItemId,
                    flaggedIgnored = entry.flaggedIgnored,
                    createdAt = entry.createdAt ?: 0L
                )
            }
            accountItems + imports + passkeys
        }
        return passwordItems + permanentDeletionItems + cxfItems
    }

    private suspend fun upsertCxfItem(database: fi.iki.ede.db.SafeDatabase, item: SyncItem) {
        require(!item.isDeleted && item.cxfAccountId.isNotBlank() &&
            (item.kind == "CXF_ACCOUNT" || item.cxfHash.isNotBlank())) {
            "Invalid CXF sync record"
        }
        val accountDao = database.cxfAccountDao()
        var account = accountDao.getByCxfAccountId(item.cxfAccountId)
        if (account == null) {
            val id = accountDao.insert(CXFAccount(cxfAccountId = item.cxfAccountId, email = item.cxfAccountEmail))
            account = accountDao.getByCxfAccountId(item.cxfAccountId)
            require(account?.id == id) { "Failed to create CXF account" }
        } else if (account.cachedDecryptedEmail != item.cxfAccountEmail) {
            accountDao.update(account.copy(encryptedEmail = item.cxfAccountEmail.encrypt()))
        }
        if (item.kind == "CXF_ACCOUNT") return
        val accountId = requireNotNull(account.id)
        if (item.kind == "CXF_IMPORT") {
            val dao = database.cxfImportDao()
            val existing = dao.getByAccountId(accountId).find {
                it.hash == item.cxfHash && it.cachedDecryptedCxfItemId == item.sourceCxfItemId
            }
            val record = CXFImport(
                id = existing?.id,
                accountId = accountId,
                cxfItemId = item.sourceCxfItemId,
                type = item.cxfType,
                name = item.name,
                url = item.url,
                username = item.username,
                password = item.password,
                note = item.note,
                createdAt = item.createdAt.takeIf { it > 0L },
                modifiedAt = item.modifiedAt,
                importedAt = existing?.importedAt ?: item.modifiedAt,
                flaggedIgnored = item.flaggedIgnored,
                hash = item.cxfHash
            )
            if (existing == null) dao.insert(record) else dao.update(record)
        } else {
            val dao = database.cxfPasskeyDao()
            val existing = dao.getByAccountId(accountId).find {
                it.hash == item.cxfHash && it.cachedDecryptedCxfItemId == item.sourceCxfItemId
            }
            val record = CXFPasskey(
                id = existing?.id,
                accountId = accountId,
                cxfItemId = item.sourceCxfItemId,
                rpId = item.rpId,
                name = item.name,
                url = item.url,
                username = item.username,
                credentialId = item.credentialId,
                userHandle = item.userHandle,
                note = item.note,
                createdAt = item.createdAt.takeIf { it > 0L },
                modifiedAt = item.modifiedAt,
                importedAt = existing?.importedAt ?: item.modifiedAt,
                flaggedIgnored = item.flaggedIgnored,
                hash = item.cxfHash
            )
            if (existing == null) dao.insert(record) else dao.update(record)
        }
    }

    /**
     * Applies resolved conflict choices directly back to the local SQLite database.
     */
    fun applyResolvedConflictsToLocalDatabase(resolvedConflicts: List<SyncConflict>) {
        val db = DBHelperFactory.getDBHelper()
        db.runInTransaction {
            var currentCategories = db.database.categoryDao().getAll()
            val activeEntries = db.database.siteEntryDao().getAllActive()
            val softDeletedEntries = db.database.siteEntryDao().getAllSoftDeleted()
            val existingEntries = (activeEntries + softDeletedEntries).toMutableList()

            for (conflict in resolvedConflicts) {
                val local = conflict.localItem
                val remote = conflict.remoteItem
                val winner = winningItem(local, remote) ?: continue
                if (winner.kind == "CXF_ACCOUNT" || winner.kind == "CXF_IMPORT" || winner.kind == "CXF_PASSKEY") {
                    val loser = if (local != null && remote != null &&
                        local.copy(modifiedAt = 0L) != remote.copy(modifiedAt = 0L)) {
                        if (winner === local) remote else local
                    } else null
                    if (loser != null && !loser.isDeleted && loser.kind == winner.kind) {
                        val archiveHash = "sync-conflict-" + MerkleTreeHelper.computeItemHash(loser).take(24)
                        val archive = if (loser.kind == "CXF_ACCOUNT") {
                            loser.copy(
                                cxfItemId = "cxf-account:$archiveHash",
                                cxfAccountId = archiveHash,
                                name = "Conflict copy: ${loser.name}"
                            )
                        } else {
                            loser.copy(name = "Conflict copy: ${loser.name}", cxfHash = archiveHash)
                        }
                        upsertCxfItem(db.database, archive)
                    }
                    upsertCxfItem(db.database, winner)
                    continue
                }
                require(winner.kind == "PASSWORD") { "Unsupported sync item kind" }
                val targetName = conflict.selectedName
                val targetUser = conflict.selectedUsername
                val targetPass = conflict.selectedPassword
                val targetNote = conflict.selectedNote
                val targetUrl = conflict.selectedUrl
                val targetDeleted = conflict.selectedIsDeleted
                val permanentDeletionWins = conflict.selectedIsDeleted &&
                    listOfNotNull(local, remote).any { it.isPermanentDeletion && it.isDeleted }
                val winningTime = maxOf(conflict.localItem?.modifiedAt ?: 0L, conflict.remoteItem?.modifiedAt ?: 0L)
                val delTimeSeconds = if (winningTime > 0L) {
                    if (winningTime > 5_000_000_000L) winningTime / 1000L else winningTime
                } else kotlin.time.Clock.System.now().epochSeconds
                val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val winningMillis = if (winningTime > 0L) {
                    if (winningTime < 5_000_000_000L) winningTime * 1000L else winningTime
                } else nowMillis
                var targetCatId = currentCategories.firstOrNull()?.id ?: 1L
                if (!permanentDeletionWins) {
                    val itemCatName = conflict.selectedCategoryName.ifBlank { "General Vault" }
                    var resolvedCat = currentCategories.find { it.plainName == itemCatName }
                    if (resolvedCat == null) {
                        val newCat = DecryptableCategoryEntry().apply {
                            encryptedName = itemCatName.encrypt()
                        }
                        db.database.categoryDao().insert(newCat)
                        currentCategories = db.database.categoryDao().getAll()
                        resolvedCat = currentCategories.find { it.plainName == itemCatName }
                    }
                    targetCatId = resolvedCat?.id ?: targetCatId
                }

                // Keep the losing credential as an ordinary encrypted vault entry. Both
                // peers derive the same copy ID, so later syncs do not create more copies.
                val loser = if (local != null && remote != null &&
                    local.copy(cxfItemId = "", modifiedAt = 0L) != remote.copy(cxfItemId = "", modifiedAt = 0L)) {
                    if (winner === local) remote else local
                } else null
                if (loser != null && !loser.isDeleted && winner.kind == "PASSWORD" && !permanentDeletionWins) {
                    // The loser hash is stable on every peer. Including the local
                    // conflict ID would create a different archive ID on each
                    // database when their pre-sync row IDs differ.
                    val canonicalLoser = loser.copy(cxfItemId = conflict.conflictId)
                    val archiveId = "conflict:${MerkleTreeHelper.computeItemHash(canonicalLoser).take(24)}"
                    if (existingEntries.none { it.plainExtensions["cxfItemId"]?.contains(archiveId) == true }) {
                        val archive = DecryptableSiteEntry(categoryId = targetCatId).apply {
                            description = "Conflict copy: ${loser.name}".encrypt()
                            username = loser.username.encrypt()
                            password = loser.password.encrypt()
                            note = loser.note.encrypt()
                            website = loser.url.encrypt()
                            passwordChangedDate = kotlin.time.Instant.fromEpochMilliseconds(loser.modifiedAt)
                            extensions = encryptExtension(mapOf("cxfItemId" to setOf(archiveId)))
                        }
                        archive.id = db.database.siteEntryDao().insert(archive)
                        existingEntries.add(archive)
                    }
                }

                val sourceIds = setOfNotNull(
                    conflict.conflictId,
                    local?.cxfItemId,
                    remote?.cxfItemId
                )
                val existing = existingEntries.find { entry ->
                    entry.plainExtensions["cxfItemId"]?.any(sourceIds::contains) == true ||
                        (entry.plainExtensions["cxfItemId"].isNullOrEmpty() &&
                            "site-entry:${entry.id}" in sourceIds)
                }

                if (permanentDeletionWins) {
                    existing?.let { db.database.siteEntryDao().deleteById(requireNotNull(it.id)) }
                    existingEntries.removeAll { it.id == existing?.id }
                    db.database.syncTombstoneDao().deleteByIds(sourceIds.toList())
                    db.database.syncTombstoneDao().upsert(
                        fi.iki.ede.db.SyncTombstone(conflict.conflictId, delTimeSeconds)
                    )
                    continue
                }

                // A later active edit supersedes and removes the old permanent-deletion marker.
                if (!targetDeleted) {
                    db.database.syncTombstoneDao().deleteByIds(sourceIds.toList())
                }

                if (existing != null) {
                    existing.categoryId = targetCatId
                    existing.description = targetName.encrypt()
                    existing.username = targetUser.encrypt()
                    existing.password = targetPass.encrypt()
                    existing.note = targetNote.encrypt()
                    existing.website = targetUrl.encrypt()
                    existing.deleted = if (targetDeleted) delTimeSeconds else 0L
                    existing.passwordChangedDate = kotlin.time.Instant.fromEpochMilliseconds(winningMillis)
                    val extMap = existing.plainExtensions.toMutableMap()
                    extMap["cxfItemId"] = setOf(conflict.conflictId)
                    existing.extensions = existing.encryptExtension(extMap)
                    db.database.siteEntryDao().update(existing)
                } else {
                    val newEntry = DecryptableSiteEntry(categoryId = targetCatId).apply {
                        description = targetName.encrypt()
                        username = targetUser.encrypt()
                        password = targetPass.encrypt()
                        note = targetNote.encrypt()
                        website = targetUrl.encrypt()
                        deleted = if (targetDeleted) delTimeSeconds else 0L
                        passwordChangedDate = kotlin.time.Instant.fromEpochMilliseconds(winningMillis)
                        extensions = encryptExtension(mapOf("cxfItemId" to setOf(conflict.conflictId)))
                    }
                    newEntry.id = db.database.siteEntryDao().insert(newEntry)
                    existingEntries.add(newEntry)
                }
            }
        }
    }
}


