package fi.iki.ede.safe.ui.sync

import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseLog
import fi.iki.ede.logger.firebaseRecordException
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
                firebaseLog("SafeSyncNet", "Sync server started on port ${ss.localPort}")
                break
            } catch (_: Exception) {}
        }
        if (serverSocket == null) return

        thread(start = true, isDaemon = true) {
            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.soTimeout = 30000
                    firebaseLog("SafeSyncNet", "Server accepted client connection from ${clientSocket.inetAddress?.hostAddress}:${clientSocket.port}")
                    thread {
                        handleClient(clientSocket, pin, onPayloadReceived)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Logger.e(TAG, "Error in sync server loop", e)
                    firebaseRecordException("SafeSyncNet server loop error", e)
                }
            }
        }
    }

    private fun readBoundedLine(reader: BufferedReader, maxBytes: Int = MAX_SYNC_PAYLOAD_BYTES): String? {
        val sb = StringBuilder()
        var count = 0
        while (true) {
            val ch = reader.read()
            if (ch == -1) {
                return if (sb.isNotEmpty()) sb.toString() else null
            }
            if (ch == '\n'.code) {
                val s = sb.toString()
                return if (s.endsWith('\r')) s.dropLast(1) else s
            }
            sb.append(ch.toChar())
            count++
            if (count > maxBytes) {
                throw IllegalStateException("Sync payload exceeded maximum allowed limit of $maxBytes bytes")
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

                // 1. Read Client Ephemeral Public Key (Handshake)
                val clientHelloLine = readBoundedLine(reader) ?: return
                val clientPublicKey = SyncPairingSession.parseHelloMessage(clientHelloLine)

                // 2. Generate Server Ephemeral Keypair and Send Server Hello
                val serverKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                writer.println(SyncPairingSession.createHelloMessage(serverKeyPair))
                writer.flush()

                // 3. Read and Decrypt Payload (Authenticated via ECDH + PIN)
                val incomingLine = readBoundedLine(reader) ?: return
                val decryptedJson = SyncPairingSession.decryptPayload(
                    message = incomingLine,
                    pin = pin,
                    myKeyPair = serverKeyPair,
                    peerPublicKey = clientPublicKey
                )

                // 4. Process Application Vault Changes
                val responseJson = onPayloadReceived(decryptedJson)

                // 5. Encrypt and Send Response
                val encryptedResponse = SyncPairingSession.encryptPayload(
                    plainJson = responseJson,
                    pin = pin,
                    myKeyPair = serverKeyPair,
                    peerPublicKey = clientPublicKey
                )
                writer.println(encryptedResponse)
                writer.flush()
                Logger.d(TAG, "Client request processed successfully (ECDH v2)")
                firebaseLog("SafeSyncNet", "Server response sent (length=${encryptedResponse.length})")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling client sync request", e)
            firebaseRecordException("SafeSyncNet error handling client request: ${e::class.simpleName}", e)
            try {
                PrintWriter(socket.getOutputStream(), true).apply {
                    println("ERROR:Failed")
                    flush()
                }
            } catch (_: Exception) {}
        }
    }

    actual fun stopServer() {
        isRunning = false
        boundPort = -1
        try {
            serverSocket?.close()
            firebaseLog("SafeSyncNet", "Sync server stopped")
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
        localPayloadJson: String,
        onProgress: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            onProgress?.invoke("Connecting to $hostAddress:$port...")
            firebaseLog("SafeSyncNet", "Client connecting to $hostAddress:$port (connectTimeout=5000ms, soTimeout=60000ms)...")
            socket.connect(java.net.InetSocketAddress(hostAddress, port), 5000)
            socket.soTimeout = 60000
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)

                // 1. Generate Client Ephemeral Keypair and Send Hello
                val clientKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                writer.println(SyncPairingSession.createHelloMessage(clientKeyPair))
                writer.flush()

                // 2. Read Server Hello (ECDH Public Key)
                onProgress?.invoke("Negotiating ECDH key agreement...")
                val serverHelloLine = readBoundedLine(reader)
                    ?: throw IllegalStateException("Target device closed connection during handshake")
                if (serverHelloLine.startsWith("ERROR:")) {
                    throw IllegalArgumentException("Target device rejected connection: $serverHelloLine")
                }
                val serverPublicKey = SyncPairingSession.parseHelloMessage(serverHelloLine)

                // 3. Encrypt and Send Vault Payload
                onProgress?.invoke("Encrypting vault data...")
                val encryptedPayload = SyncPairingSession.encryptPayload(
                    plainJson = localPayloadJson,
                    pin = pin,
                    myKeyPair = clientKeyPair,
                    peerPublicKey = serverPublicKey
                )
                val payloadKb = encryptedPayload.length / 1024
                onProgress?.invoke("Transferring vault data ($payloadKb KB)...")
                firebaseLog("SafeSyncNet", "Client sending payload of length ${encryptedPayload.length} to $hostAddress:$port...")
                writer.println(encryptedPayload)
                writer.flush()

                // 4. Wait for Encrypted Response
                onProgress?.invoke("Waiting for remote device to process changes...")
                firebaseLog("SafeSyncNet", "Client waiting for response from $hostAddress:$port...")
                val responseLine = readBoundedLine(reader)
                    ?: throw IllegalStateException("No response received from target device (connection closed)")

                firebaseLog("SafeSyncNet", "Client received response of length ${responseLine.length} from $hostAddress:$port")
                if (responseLine.startsWith("ERROR:")) {
                    throw IllegalArgumentException("Target device rejected connection: $responseLine")
                }

                // 5. Decrypt and Authenticate Response
                onProgress?.invoke("Decrypting response...")
                SyncPairingSession.decryptPayload(
                    message = responseLine,
                    pin = pin,
                    myKeyPair = clientKeyPair,
                    peerPublicKey = serverPublicKey
                )
            }
        } catch (e: Exception) {
            firebaseRecordException("SafeSyncNet client error with $hostAddress:$port: ${e::class.simpleName}", e)
            throw e
        }
    }
}
