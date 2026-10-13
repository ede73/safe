package fi.iki.ede.safe.ui.sync

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val hostAddress: String,
    val port: Int
)

const val SAFE_MDNS_SERVICE_TYPE = "_safe-sync._tcp."
const val SAFE_SYNC_DEFAULT_PORT = 58443

expect class LocalDeviceSyncManager() {
    fun startAdvertising(deviceName: String, port: Int)
    fun stopAdvertising()
    fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (DiscoveredDevice) -> Unit
    )
    fun stopDiscovery()
}
