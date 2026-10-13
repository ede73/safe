package fi.iki.ede.safe.ui.sync

import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCSignatureOverride

actual class LocalDeviceSyncManager {

    private var netService: NSNetService? = null
    private var netServiceBrowser: NSNetServiceBrowser? = null
    private val resolvingServices = mutableListOf<NSNetService>()
    private var myDeviceName: String = ""
    private var myPort: Int = -1

    actual fun startAdvertising(deviceName: String, port: Int) {
        stopAdvertising()
        myDeviceName = deviceName
        myPort = port
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
        resolvingServices.clear()
        netServiceBrowser = NSNetServiceBrowser().apply {
            val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
                @ObjCSignatureOverride
                override fun netServiceBrowser(
                    browser: NSNetServiceBrowser,
                    didFindService: NSNetService,
                    moreComing: Boolean
                ) {
                    if (didFindService.name.equals(myDeviceName, ignoreCase = true)) {
                        return
                    }
                    resolvingServices.add(didFindService)
                    val serviceDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
                        @ObjCSignatureOverride
                        override fun netServiceDidResolveAddress(sender: NSNetService) {
                            if (sender.name.equals(myDeviceName, ignoreCase = true)) return
                            val resolvedPort = if (sender.port.toInt() > 0) sender.port.toInt() else SAFE_SYNC_DEFAULT_PORT
                            val device = DiscoveredDevice(
                                id = sender.name,
                                name = sender.name,
                                hostAddress = "127.0.0.1",
                                port = resolvedPort
                            )
                            onDeviceFound(device)
                        }

                        @ObjCSignatureOverride
                        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
                            if (sender.name.equals(myDeviceName, ignoreCase = true)) return
                            // Fallback if mDNS resolve times out on simulator loopback
                            val device = DiscoveredDevice(
                                id = sender.name,
                                name = sender.name,
                                hostAddress = "127.0.0.1",
                                port = SAFE_SYNC_DEFAULT_PORT
                            )
                            onDeviceFound(device)
                        }
                    }
                    didFindService.setDelegate(serviceDelegate)
                    didFindService.resolveWithTimeout(5.0)
                }

                @ObjCSignatureOverride
                override fun netServiceBrowser(
                    browser: NSNetServiceBrowser,
                    didRemoveService: NSNetService,
                    moreComing: Boolean
                ) {
                    resolvingServices.remove(didRemoveService)
                    val device = DiscoveredDevice(
                        id = didRemoveService.name,
                        name = didRemoveService.name,
                        hostAddress = "",
                        port = if (didRemoveService.port.toInt() > 0) didRemoveService.port.toInt() else SAFE_SYNC_DEFAULT_PORT
                    )
                    onDeviceLost(device)
                }
            }
            setDelegate(browserDelegate)
            searchForServicesOfType("_safe-sync._tcp.", inDomain = "local.")
        }
    }

    actual fun stopDiscovery() {
        resolvingServices.forEach { it.stop() }
        resolvingServices.clear()
        netServiceBrowser?.stop()
        netServiceBrowser = null
    }
}
