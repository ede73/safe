package fi.iki.ede.safe.ui.sync

import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCSignatureOverride

actual class LocalDeviceSyncManager {

    private var netService: NSNetService? = null
    private var netServiceBrowser: NSNetServiceBrowser? = null

    actual fun startAdvertising(deviceName: String, port: Int) {
        stopAdvertising()
        netService = NSNetService(
            domain = "local.",
            type = "_safe-sync._tcp.",
            name = deviceName,
            port = port
        ).apply {
            publish()
        }
    }

    actual fun stopAdvertising() {
        netService?.stop()
        netService = null
    }

    actual fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (DiscoveredDevice) -> Unit
    ) {
        stopDiscovery()
        netServiceBrowser = NSNetServiceBrowser().apply {
            val delegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
                @ObjCSignatureOverride
                override fun netServiceBrowser(
                    browser: NSNetServiceBrowser,
                    didFindService: NSNetService,
                    moreComing: Boolean
                ) {
                    val device = DiscoveredDevice(
                        id = didFindService.name,
                        name = didFindService.name,
                        hostAddress = "127.0.0.1",
                        port = didFindService.port.toInt()
                    )
                    onDeviceFound(device)
                }

                @ObjCSignatureOverride
                override fun netServiceBrowser(
                    browser: NSNetServiceBrowser,
                    didRemoveService: NSNetService,
                    moreComing: Boolean
                ) {
                    val device = DiscoveredDevice(
                        id = didRemoveService.name,
                        name = didRemoveService.name,
                        hostAddress = "",
                        port = didRemoveService.port.toInt()
                    )
                    onDeviceLost(device)
                }
            }
            setDelegate(delegate)
            searchForServicesOfType("_safe-sync._tcp.", inDomain = "local.")
        }
    }

    actual fun stopDiscovery() {
        netServiceBrowser?.stop()
        netServiceBrowser = null
    }
}
