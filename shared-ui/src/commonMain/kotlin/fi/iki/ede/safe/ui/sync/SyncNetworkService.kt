package fi.iki.ede.safe.ui.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val syncJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

@Serializable
data class SyncPayload(
    val items: List<SyncItem>,
    val merkleRoot: String = "",
    val deviceName: String = ""
)

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
