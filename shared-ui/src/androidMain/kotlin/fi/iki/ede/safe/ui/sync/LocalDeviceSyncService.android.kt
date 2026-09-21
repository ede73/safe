package fi.iki.ede.safe.ui.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import fi.iki.ede.logger.Logger

private const val TAG = "LocalDeviceSyncAndroid"

actual class LocalDeviceSyncManager {

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun initialize(context: Context) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    actual fun startAdvertising(deviceName: String, port: Int) {
        val manager = nsdManager ?: return
        stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SAFE_MDNS_SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service Registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Logger.e(TAG, "mDNS Registration failed: $arg1")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service Unregistered")
            }

            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Logger.e(TAG, "mDNS Unregistration failed: $arg1")
            }
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start mDNS advertising", e)
        }
    }

    actual fun stopAdvertising() {
        val listener = registrationListener ?: return
        try {
            nsdManager?.unregisterService(listener)
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping mDNS advertising", e)
        } finally {
            registrationListener = null
        }
    }

    actual fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (DiscoveredDevice) -> Unit
    ) {
        val manager = nsdManager ?: return
        stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Logger.d(TAG, "mDNS Discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("safe-sync")) {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Logger.e(TAG, "mDNS Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val host = resolvedInfo.host?.hostAddress ?: "127.0.0.1"
                            val device = DiscoveredDevice(
                                id = resolvedInfo.serviceName,
                                name = resolvedInfo.serviceName,
                                hostAddress = host,
                                port = resolvedInfo.port
                            )
                            onDeviceFound(device)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val device = DiscoveredDevice(
                    id = serviceInfo.serviceName,
                    name = serviceInfo.serviceName,
                    hostAddress = "",
                    port = serviceInfo.port
                )
                onDeviceLost(device)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Logger.d(TAG, "mDNS Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG, "mDNS Start discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG, "mDNS Stop discovery failed: $errorCode")
            }
        }

        try {
            manager.discoverServices(SAFE_MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start mDNS discovery", e)
        }
    }

    actual fun stopDiscovery() {
        val listener = discoveryListener ?: return
        try {
            nsdManager?.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping mDNS discovery", e)
        } finally {
            discoveryListener = null
        }
    }
}
