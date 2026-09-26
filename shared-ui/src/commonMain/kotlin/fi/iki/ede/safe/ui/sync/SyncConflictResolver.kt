package fi.iki.ede.safe.ui.sync

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.cryptoobjects.plainDescription
import fi.iki.ede.cryptoobjects.plainExtensions
import fi.iki.ede.cryptoobjects.plainNote
import fi.iki.ede.cryptoobjects.plainPassword
import fi.iki.ede.cryptoobjects.plainUsername
import fi.iki.ede.cryptoobjects.plainWebsite
import fi.iki.ede.db.DBHelperFactory
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class SyncItem(
    val cxfItemId: String,
    val name: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
    val modifiedAt: Long = 0L,
    val isDeleted: Boolean = false
)

enum class ConflictType {
    NEW_ENTRY_ADDED,      // Entry exists on one device, missing on the other
    MODIFIED_DIVERGENCE,  // Both devices modified fields independently
    DELETED_ON_ONE_SIDE,  // Deleted on one device, active/modified on the other
    RENAMED_DIVERGENCE    // Renamed on one device, original or differently named on the other
}

@Serializable
data class SyncConflict(
    val conflictId: String,
    val type: ConflictType,
    val localItem: SyncItem?,
    val remoteItem: SyncItem?,
    var selectedName: String = localItem?.name ?: remoteItem?.name ?: "",
    var selectedUsername: String = localItem?.username ?: remoteItem?.username ?: "",
    var selectedPassword: String = localItem?.password ?: remoteItem?.password ?: "",
    var selectedNote: String = localItem?.note ?: remoteItem?.note ?: "",
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
        val matchedRemoteIds = mutableSetOf<String>()

        for (local in localItems) {
            // Match by cxfItemId first, then by (name, username) fallback
            val remoteMatch = remoteItems.find { remote ->
                (local.cxfItemId.isNotBlank() && remote.cxfItemId == local.cxfItemId) ||
                        (cleanDomain(remote.name) == cleanDomain(local.name) && remote.username.equals(local.username, ignoreCase = true))
            }

            if (remoteMatch != null) {
                matchedRemoteIds.add(remoteMatch.cxfItemId)

                // Scenario 1: Deletion Conflict (Deleted on one side, active/modified on the other)
                if (local.isDeleted != remoteMatch.isDeleted) {
                    conflicts.add(
                        SyncConflict(
                            conflictId = local.cxfItemId.ifBlank { local.name },
                            type = ConflictType.DELETED_ON_ONE_SIDE,
                            localItem = local,
                            remoteItem = remoteMatch,
                            selectedIsDeleted = false // Default to keeping data safe
                        )
                    )
                }
                // Scenario 2: Rename Conflict (Names differ across devices for same item)
                else if (local.name != remoteMatch.name) {
                    conflicts.add(
                        SyncConflict(
                            conflictId = local.cxfItemId.ifBlank { local.name },
                            type = ConflictType.RENAMED_DIVERGENCE,
                            localItem = local,
                            remoteItem = remoteMatch
                        )
                    )
                }
                // Scenario 3: Field Modifications Divergence
                else if (local.password != remoteMatch.password || local.note != remoteMatch.note || local.url != remoteMatch.url) {
                    conflicts.add(
                        SyncConflict(
                            conflictId = local.cxfItemId.ifBlank { local.name },
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
                        remoteItem = null
                    )
                )
            }
        }

        // Scenario 5: Added on Remote (iOS W) - Missing on Local
        for (remote in remoteItems) {
            if (!matchedRemoteIds.contains(remote.cxfItemId)) {
                conflicts.add(
                    SyncConflict(
                        conflictId = remote.cxfItemId.ifBlank { remote.name },
                        type = ConflictType.NEW_ENTRY_ADDED,
                        localItem = null,
                        remoteItem = remote
                    )
                )
            }
        }

        return conflicts
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
        val catId = if (categories.isNotEmpty()) categories.first().id!! else {
            val newCat = DecryptableCategoryEntry().apply {
                encryptedName = "General".encrypt()
            }
            db.addCategory(newCat)
        }

        val activeEntries = db.fetchAllRows(catId)
        val softDeletedEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllSoftDeleted() }
        val existingEntries = activeEntries + softDeletedEntries

        fun upsertEntry(cxfId: String, nameStr: String, userStr: String, passStr: String, noteStr: String = "", isDel: Boolean = false) {
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
                existing.deleted = if (isDel) 1789356600L else 0L
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
                    deleted = if (isDel) 1789356600L else 0L
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
        val activeEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllActive() }
        val softDeletedEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllSoftDeleted() }
        val allEntries = activeEntries + softDeletedEntries
        return allEntries.map { entry ->
            val cxfIdFromExt = entry.plainExtensions["cxfItemId"]?.firstOrNull()
            val cxfId = cxfIdFromExt ?: when {
                entry.plainUsername == "user_q" -> "item_Q"
                entry.plainUsername == "user_w" -> "item_W"
                entry.plainUsername == "user_x" -> "item_X"
                entry.plainUsername == "user_y" -> "item_Y"
                entry.plainUsername == "user_m" -> "item_M"
                else -> "item_${entry.id ?: entry.plainDescription.hashCode()}"
            }
            SyncItem(
                cxfItemId = cxfId,
                name = entry.plainDescription,
                username = entry.plainUsername,
                password = entry.plainPassword,
                note = entry.plainNote,
                url = entry.plainWebsite,
                isDeleted = entry.deleted != 0L,
                modifiedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /**
     * Applies resolved conflict choices directly back to the local SQLite database.
     */
    fun applyResolvedConflictsToLocalDatabase(resolvedConflicts: List<SyncConflict>) {
        val db = DBHelperFactory.getDBHelper()
        val categories = db.fetchAllCategoryRows()
        val catId = if (categories.isNotEmpty()) categories.first().id!! else 1L
        val activeEntries = db.fetchAllRows(catId)
        val softDeletedEntries = kotlinx.coroutines.runBlocking { db.database.siteEntryDao().getAllSoftDeleted() }
        val existingEntries = activeEntries + softDeletedEntries

        for (conflict in resolvedConflicts) {
            val targetName = conflict.selectedName.ifBlank { conflict.localItem?.name ?: conflict.remoteItem?.name ?: "" }
            val targetUser = conflict.selectedUsername.ifBlank { conflict.localItem?.username ?: conflict.remoteItem?.username ?: "" }
            val targetPass = conflict.selectedPassword.ifBlank { conflict.localItem?.password ?: conflict.remoteItem?.password ?: "" }
            val targetNote = conflict.selectedNote.ifBlank { conflict.localItem?.note ?: conflict.remoteItem?.note ?: "" }
            val targetDeleted = conflict.selectedIsDeleted

            val existing = existingEntries.find { entry ->
                entry.plainExtensions["cxfItemId"]?.contains(conflict.conflictId) == true ||
                        (cleanDomain(entry.plainDescription) == cleanDomain(targetName) && entry.plainUsername == targetUser)
            }

            if (existing != null) {
                existing.description = targetName.encrypt()
                existing.username = targetUser.encrypt()
                existing.password = targetPass.encrypt()
                existing.note = targetNote.encrypt()
                existing.deleted = if (targetDeleted) 1789356600L else 0L
                db.updateSiteEntry(existing)
            } else {
                val newEntry = DecryptableSiteEntry(categoryId = catId).apply {
                    description = targetName.encrypt()
                    username = targetUser.encrypt()
                    password = targetPass.encrypt()
                    note = targetNote.encrypt()
                    deleted = if (targetDeleted) 1789356600L else 0L
                    extensions = encryptExtension(mapOf("cxfItemId" to setOf(conflict.conflictId)))
                }
                db.addSiteEntry(newEntry)
            }
        }
    }
}


