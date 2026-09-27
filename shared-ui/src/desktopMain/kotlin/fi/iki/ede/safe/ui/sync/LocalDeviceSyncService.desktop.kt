package fi.iki.ede.safe.ui.sync

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import kotlin.concurrent.thread

private const val MULTICAST_GROUP = "239.255.255.250"
private const val MULTICAST_PORT = 53535

actual class LocalDeviceSyncManager {

    private var advertisingThread: Thread? = null
    private var discoveryThread: Thread? = null
    @Volatile private var isAdvertising = false
    @Volatile private var isDiscovering = false

    actual fun startAdvertising(deviceName: String, port: Int) {
        stopAdvertising()
        isAdvertising = true
        advertisingThread = thread(start = true, isDaemon = true) {
            try {
                val group = InetAddress.getByName(MULTICAST_GROUP)
                val socket = MulticastSocket()
                val payload = "SAFE_SYNC_BEACON:$deviceName:$port"
                val bytes = payload.encodeToByteArray()

                while (isAdvertising) {
                    val packet = DatagramPacket(bytes, bytes.size, group, MULTICAST_PORT)
                    socket.send(packet)
                    Thread.sleep(2000)
                }
                socket.close()
            } catch (e: Exception) {
                // Ignore background thread interruptions
            }
        }
    }

    actual fun stopAdvertising() {
        isAdvertising = false
        advertisingThread?.interrupt()
        advertisingThread = null
    }

    actual fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (DiscoveredDevice) -> Unit
    ) {
        stopDiscovery()
        isDiscovering = true
        discoveryThread = thread(start = true, isDaemon = true) {
            try {
                val group = InetAddress.getByName(MULTICAST_GROUP)
                val socket = MulticastSocket(MULTICAST_PORT)
                socket.joinGroup(group)
                val buffer = ByteArray(1024)

                while (isDiscovering) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length).trim()
                    if (message.startsWith("SAFE_SYNC_BEACON:")) {
                        val parts = message.split(":")
                        if (parts.size >= 3) {
                            val name = parts[1]
                            val port = parts[2].toIntOrNull() ?: 8443
                            val host = packet.address.hostAddress ?: "127.0.0.1"
                            val device = DiscoveredDevice(
                                id = "$name@$host",
                                name = name,
                                hostAddress = host,
                                port = port
                            )
                            onDeviceFound(device)
                        }
                    }
                }
                socket.leaveGroup(group)
                socket.close()
            } catch (e: Exception) {
                // Ignore background thread interruptions
            }
        }
    }

    actual fun stopDiscovery() {
        isDiscovering = false
        discoveryThread?.interrupt()
        discoveryThread = null
    }
}
