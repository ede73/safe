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
        val candidatePorts = if (port == 0) listOf(0) else listOf(port, port + 1, port + 2, 0).distinct()
        for (p in candidatePorts) {
            try {
                val ss = ServerSocket(p)
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
                    clientSocket.soTimeout = 30000
                    thread {
                        handleClient(clientSocket, pin, onPayloadReceived)
                    }
                }
            } catch (_: Exception) {}
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

                // 1. Read Client Hello
                val clientHelloLine = readBoundedLine(reader) ?: return
                val clientPublicKey = SyncPairingSession.parseHelloMessage(clientHelloLine)

                // 2. Generate Server Keypair and Send Hello
                val serverKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                writer.println(SyncPairingSession.createHelloMessage(serverKeyPair))
                writer.flush()

                // 3. Read and Decrypt Payload
                val incomingLine = readBoundedLine(reader) ?: return
                val decryptedJson = SyncPairingSession.decryptPayload(
                    message = incomingLine,
                    pin = pin,
                    myKeyPair = serverKeyPair,
                    peerPublicKey = clientPublicKey
                )

                // 4. Process Application Vault Changes
                val responseJson = onPayloadReceived(decryptedJson)

                // 5. Send Encrypted Response
                val encryptedResponse = SyncPairingSession.encryptPayload(
                    plainJson = responseJson,
                    pin = pin,
                    myKeyPair = serverKeyPair,
                    peerPublicKey = clientPublicKey
                )
                writer.println(encryptedResponse)
                writer.flush()
            }
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        onProgress?.invoke("Connecting to $hostAddress:$port...")
        socket.connect(java.net.InetSocketAddress(hostAddress, port), 5000)
        socket.soTimeout = 60000
        socket.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)

            // 1. Send Client Hello
            val clientKeyPair = SyncPairingSession.generateEphemeralKeyPair()
            writer.println(SyncPairingSession.createHelloMessage(clientKeyPair))
            writer.flush()

            // 2. Read Server Hello
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
            writer.println(encryptedPayload)
            writer.flush()

            // 4. Wait for Response
            onProgress?.invoke("Waiting for remote device to process changes...")
            val responseLine = readBoundedLine(reader)
                ?: throw IllegalStateException("No response received from target device (connection closed)")

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
    }
}
