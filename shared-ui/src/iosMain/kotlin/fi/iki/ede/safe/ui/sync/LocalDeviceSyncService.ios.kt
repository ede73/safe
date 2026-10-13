package fi.iki.ede.safe.ui.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.sockaddr
import platform.posix.sockaddr_in

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
                        @OptIn(ExperimentalForeignApi::class)
                        private fun extractHostAddress(sender: NSNetService): String {
                            val addresses = sender.addresses
                            if (addresses != null) {
                                for (item in addresses) {
                                    val data = item as? NSData ?: continue
                                    val bytes = data.bytes ?: continue
                                    val length = data.length.toInt()
                                    if (length >= sizeOf<sockaddr>().toInt()) {
                                        val sa = bytes.reinterpret<sockaddr>()
                                        if (sa.pointed.sa_family.toInt() == AF_INET && length >= sizeOf<sockaddr_in>().toInt()) {
                                            val saIn = bytes.reinterpret<sockaddr_in>()
                                            val ptr = saIn.pointed.sin_addr.ptr.reinterpret<UByteVar>()
                                            val ip = "${ptr[0]}.${ptr[1]}.${ptr[2]}.${ptr[3]}"
                                            if (ip != "0.0.0.0" && ip != "127.0.0.1") {
                                                return ip
                                            }
                                        }
                                    }
                                }
                            }
                            val hostName = sender.hostName?.removeSuffix(".")
                            if (!hostName.isNullOrBlank() && !hostName.equals("localhost", ignoreCase = true)) {
                                return hostName
                            }
                            return "127.0.0.1"
                        }

                        @ObjCSignatureOverride
                        override fun netServiceDidResolveAddress(sender: NSNetService) {
                            if (sender.name.equals(myDeviceName, ignoreCase = true)) return
                            val resolvedPort = if (sender.port.toInt() > 0) sender.port.toInt() else SAFE_SYNC_DEFAULT_PORT
                            val host = extractHostAddress(sender)
                            val device = DiscoveredDevice(
                                id = sender.name,
                                name = sender.name,
                                hostAddress = host,
                                port = resolvedPort
                            )
                            onDeviceFound(device)
                        }

                        @ObjCSignatureOverride
                        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
                            if (sender.name.equals(myDeviceName, ignoreCase = true)) return
                            val host = sender.hostName?.removeSuffix(".")?.ifBlank { null } ?: "127.0.0.1"
                            val device = DiscoveredDevice(
                                id = sender.name,
                                name = sender.name,
                                hostAddress = host,
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
