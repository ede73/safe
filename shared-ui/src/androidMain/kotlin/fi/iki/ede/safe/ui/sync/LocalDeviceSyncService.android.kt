package fi.iki.ede.safe.ui.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseLog

private const val TAG = "LocalDeviceSyncAndroid"

actual class LocalDeviceSyncManager {

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var myDeviceName: String = ""

    init {
        try {
            val appClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = appClass.getMethod("currentApplication")
            val app = currentAppMethod.invoke(null) as? Context
            if (app != null) {
                nsdManager = app.getSystemService(Context.NSD_SERVICE) as? NsdManager
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get Application Context for NsdManager", e)
        }
    }

    fun initialize(context: Context) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    actual fun startAdvertising(deviceName: String, port: Int) {
        myDeviceName = deviceName
        val manager = nsdManager ?: return
        stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SAFE_MDNS_SERVICE_TYPE.trimEnd('.')
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service Registered: ${NsdServiceInfo.serviceName}")
                firebaseLog("SafeSyncDiscovery", "Service registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Logger.e(TAG, "mDNS Registration failed: $arg1")
                firebaseLog("SafeSyncDiscovery", "Service registration failed: code=$arg1")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service Unregistered")
                firebaseLog("SafeSyncDiscovery", "Service unregistered")
            }

            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {
                Logger.e(TAG, "mDNS Unregistration failed: $arg1")
                firebaseLog("SafeSyncDiscovery", "Service unregistration failed: code=$arg1")
            }
        }

        try {
            firebaseLog("SafeSyncDiscovery", "Registering service: name=$deviceName, port=$port, type=${serviceInfo.serviceType}")
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start mDNS advertising", e)
            firebaseLog("SafeSyncDiscovery", "Failed to start mDNS advertising: ${e.message}")
        }
    }

    actual fun stopAdvertising() {
        val listener = registrationListener ?: return
        try {
            nsdManager?.unregisterService(listener)
            firebaseLog("SafeSyncDiscovery", "Stopped advertising")
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
                firebaseLog("SafeSyncDiscovery", "Discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Logger.d(TAG, "mDNS Service found: ${serviceInfo.serviceName}")
                firebaseLog("SafeSyncDiscovery", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceName.equals(myDeviceName, ignoreCase = true)) {
                    return
                }
                if (serviceInfo.serviceType.contains("safe-sync")) {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Logger.e(TAG, "mDNS Resolve failed: $errorCode")
                            firebaseLog("SafeSyncDiscovery", "Resolve failed for ${serviceInfo.serviceName}: code=$errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            if (resolvedInfo.serviceName.equals(myDeviceName, ignoreCase = true)) {
                                return
                            }
                            val rawHost = resolvedInfo.host?.hostAddress?.removePrefix("/") ?: ""
                            val host = if (rawHost.isNotBlank() && rawHost != "0.0.0.0") rawHost else "127.0.0.1"
                            firebaseLog("SafeSyncDiscovery", "Service resolved: ${resolvedInfo.serviceName} -> $host:${resolvedInfo.port}")
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
                firebaseLog("SafeSyncDiscovery", "Service lost: ${serviceInfo.serviceName}")
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
                firebaseLog("SafeSyncDiscovery", "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG, "mDNS Start discovery failed: $errorCode")
                firebaseLog("SafeSyncDiscovery", "Start discovery failed: code=$errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Logger.e(TAG, "mDNS Stop discovery failed: $errorCode")
                firebaseLog("SafeSyncDiscovery", "Stop discovery failed: code=$errorCode")
            }
        }

        try {
            manager.discoverServices(SAFE_MDNS_SERVICE_TYPE.trimEnd('.'), NsdManager.PROTOCOL_DNS_SD, discoveryListener)
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
