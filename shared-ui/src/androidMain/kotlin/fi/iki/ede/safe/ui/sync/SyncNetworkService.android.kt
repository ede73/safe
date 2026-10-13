package fi.iki.ede.safe.ui.sync

import fi.iki.ede.logger.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.Build

private const val TAG = "SyncNetworkServiceAndroid"

actual class SyncNetworkService {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    actual var boundPort: Int = -1
    actual val isAndroidGuest: Boolean = isEmulator()

    companion object {
        fun isEmulator(): Boolean {
            val fingerprint = Build.FINGERPRINT ?: ""
            val model = Build.MODEL ?: ""
            val hardware = Build.HARDWARE ?: ""
            val product = Build.PRODUCT ?: ""
            val board = Build.BOARD ?: ""
            val brand = Build.BRAND ?: ""
            val device = Build.DEVICE ?: ""
            return fingerprint.startsWith("generic") ||
                    fingerprint.startsWith("unknown") ||
                    model.contains("google_sdk") ||
                    model.contains("Emulator") ||
                    model.contains("Android SDK built for x86") ||
                    hardware.contains("goldfish") ||
                    hardware.contains("ranchu") ||
                    product.contains("sdk_gphone") ||
                    product.contains("google_sdk") ||
                    product.contains("emulator") ||
                    board.contains("goldfish") ||
                    brand.startsWith("generic") ||
                    device.startsWith("generic")
        }
    }

    actual fun startServer(
        port: Int,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    ) {
        stopServer()
        isRunning = true
        val candidatePorts = if (port == 0) listOf(0) else listOf(port, port + 1, port + 2, 0).distinct()
        for (p in candidatePorts) {
            try {
                val ss = ServerSocket(p)
                serverSocket = ss
                boundPort = ss.localPort
                Logger.d(TAG, "Sync server started on port ${ss.localPort}")
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
            } catch (e: Exception) {
                if (isRunning) {
                    Logger.e(TAG, "Error in sync server loop", e)
                }
            }
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
                Logger.d(TAG, "Client request processed successfully")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling client sync request", e)
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
        } catch (e: Exception) {
            Logger.e(TAG, "Error closing sync server socket", e)
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
        Socket(hostAddress, port).use { socket ->
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val encryptedPayload = SyncPairingSession.encryptPayload(localPayloadJson, pin)
            writer.println(encryptedPayload)

            val responseLine = reader.readLine()
                ?: throw IllegalStateException("No response received from target device")

            if (responseLine.startsWith("ERROR:")) {
                throw IllegalArgumentException("Target device rejected connection (invalid PIN)")
            }

            SyncPairingSession.decryptPayload(responseLine, pin)
        }
    }
}
