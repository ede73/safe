package fi.iki.ede.safe.ui.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SyncNetworkService {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    actual var boundPort: Int = -1
    actual val isAndroidGuest: Boolean = false

    actual fun startServer(
        port: Int,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    ) {
        stopServer()
        isRunning = true
        val candidatePorts = if (port == 0) listOf(0) else listOf(port, port + 1, port + 2, port + 3, port + 4, 0).distinct()
        for (p in candidatePorts) {
            try {
                if (p != 0) {
                    val isPortTaken = try {
                        val testSock = Socket()
                        testSock.connect(java.net.InetSocketAddress("127.0.0.1", p), 100)
                        testSock.close()
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (isPortTaken) continue
                }

                val ss = ServerSocket(p, 50, java.net.InetAddress.getByName("0.0.0.0"))
                serverSocket = ss
                boundPort = ss.localPort
                break
            } catch (_: Exception) {}
        }
        if (serverSocket == null) return

        thread(start = true, isDaemon = true) {
            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    thread {
                        handleClient(clientSocket, pin, onPayloadReceived)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleClient(
        socket: Socket,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    ) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)
                val incomingLine = reader.readLine() ?: return

                val decryptedJson = SyncPairingSession.decryptPayload(incomingLine, pin)
                val responseJson = onPayloadReceived(decryptedJson)
                val encryptedResponse = SyncPairingSession.encryptPayload(responseJson, pin)
                writer.println(encryptedResponse)
            }
        } catch (e: Exception) {
            try {
                PrintWriter(socket.getOutputStream(), true).println("ERROR:${e::class.simpleName}:${e.message}")
            } catch (_: Exception) {}
        }
    }

    actual fun stopServer() {
        isRunning = false
        boundPort = -1
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        } finally {
            serverSocket = null
        }
    }

    actual suspend fun sendPayloadToHost(
        hostAddress: String,
        port: Int,
        pin: String,
        localPayloadJson: String
    ): String = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.connect(java.net.InetSocketAddress(hostAddress, port), 3000)
        socket.soTimeout = 5000
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)

            val encryptedPayload = SyncPairingSession.encryptPayload(localPayloadJson, pin)
            writer.println(encryptedPayload)

            val responseLine = reader.readLine()
                ?: throw IllegalStateException("No response received from target device")

            if (responseLine.startsWith("ERROR:")) {
                throw IllegalArgumentException("Target device rejected connection: $responseLine")
            }

            SyncPairingSession.decryptPayload(responseLine, pin)
        }
    }
}
