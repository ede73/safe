package fi.iki.ede.safe.ui.sync

import kotlinx.serialization.Serializable

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
}
