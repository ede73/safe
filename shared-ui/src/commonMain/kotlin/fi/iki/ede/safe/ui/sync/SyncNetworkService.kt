package fi.iki.ede.safe.ui.sync

import fi.iki.ede.preferences.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val syncJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

/** Maximum allowed payload size (10 MB) to prevent unbounded memory allocation and DoS attacks. */
const val MAX_SYNC_PAYLOAD_BYTES: Int = 10 * 1024 * 1024


@Serializable
data class SyncPayload(
    val items: List<SyncItem>,
    val merkleRoot: String = "",
    val deviceName: String = "",
    val version: Int = 4,
    /** Present only in the encrypted response to an interactive PIN pairing. */
    val pairedClusterSecret: String = ""
)

internal fun parseSyncPayload(json: String): SyncPayload =
    syncJson.decodeFromString<SyncPayload>(json).also { payload ->
        require(payload.version == 4) { "Unsupported synchronization protocol version" }
        require(payload.items.all { it.kind in setOf("PASSWORD", "CXF_ACCOUNT", "CXF_IMPORT", "CXF_PASSKEY") }) {
            "Unsupported synchronization item type"
        }
        require(payload.items.all { it.cxfItemId.isNotBlank() && it.cxfItemId.length <= 4096 }) {
            "Synchronization item has an invalid identity"
        }
        require(payload.items.map { it.cxfItemId }.toSet().size == payload.items.size) {
            "Synchronization payload contains duplicate identities"
        }
        require(payload.items.all { item ->
            !item.isPermanentDeletion || (
                item.kind == "PASSWORD" && item.isDeleted && item.name.isEmpty() &&
                    item.username.isEmpty() && item.password.isEmpty() && item.note.isEmpty() &&
                    item.url.isEmpty() && item.categoryName.isEmpty() && item.modifiedAt > 0L &&
                    item.modifiedAt <= Long.MAX_VALUE / 1000L
                )
        }) { "Invalid permanent-deletion tombstone" }
    }

internal fun isInteractivePairing(pin: String): Boolean =
    pin.length == SyncPairingSession.PAIRING_CODE_LENGTH && pin.all { it in "0123456789abcdef" }

/** Exchange the persistent credential only inside an authenticated, encrypted PIN session. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun pairingRequest(json: String, pin: String): String {
    val payload = parseSyncPayload(json)
    if (!isInteractivePairing(pin)) return json
    val existing = Preferences.getVaultClusterSecret() ?: return json
    return syncJson.encodeToString(payload.copy(pairedClusterSecret = existing))
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun pairingCredentialFromRequest(json: String, pin: String, derivedSecret: String): String {
    val offered = parseSyncPayload(json).pairedClusterSecret
    if (!isInteractivePairing(pin)) return ""
    require(offered.isEmpty() || (offered.length == 64 && offered.all { it in "0123456789abcdefABCDEF" })) {
        "Invalid offered cluster credential"
    }
    require(offered.isEmpty() || !Preferences.isRevokedVaultClusterSecret(offered)) {
        "This sync cluster was revoked on this device; reset the other device before re-pairing"
    }
    val existing = Preferences.getVaultClusterSecret()
    require(existing == null || offered.isEmpty() || existing.equals(offered, ignoreCase = true)) {
        "Devices belong to different sync clusters"
    }
    return existing ?: offered.ifEmpty { derivedSecret }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun pairingResponse(json: String, pin: String, chosenSecret: String): String {
    val response = parseSyncPayload(json)
    if (!isInteractivePairing(pin)) return json
    Preferences.setVaultClusterSecret(chosenSecret)
    return syncJson.encodeToString(response.copy(pairedClusterSecret = chosenSecret))
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun receivePairingResponse(json: String, pin: String): String {
    val response = parseSyncPayload(json)
    if (!isInteractivePairing(pin)) return json
    val secret = response.pairedClusterSecret
    require(secret.length == 64 && secret.all { it in "0123456789abcdefABCDEF" }) {
        "Pairing response did not include a valid cluster credential"
    }
    require(!Preferences.isRevokedVaultClusterSecret(secret)) {
        "This sync cluster was revoked on this device; reset the other device before re-pairing"
    }
    val existing = Preferences.getVaultClusterSecret()
    require(existing == null || existing.equals(secret, ignoreCase = true)) {
        "Device belongs to a different sync cluster; unpair all devices before joining it"
    }
    Preferences.setVaultClusterSecret(secret)
    return json
}

expect class SyncNetworkService() {
    var boundPort: Int
    val isAndroidGuest: Boolean
    fun startServer(
        port: Int,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    )
    fun stopServer()

    suspend fun sendPayloadToHost(
        hostAddress: String,
        port: Int,
        pin: String,
        localPayloadJson: String,
        onProgress: ((String) -> Unit)? = null
    ): String
}
