@file:OptIn(kotlin.time.ExperimentalTime::class)

package fi.iki.ede.safe.ui.sync

import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SyncNetworkService"

actual class SyncNetworkService {
    private var serverSocket: ServerSocket? = null
    private var clientExecutor: ThreadPoolExecutor? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
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
        clientExecutor = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8))
        val candidatePorts = if (port == 0) listOf(0) else listOf(port, port + 1, port + 2, 0).distinct()
        for (p in candidatePorts) {
            try {
                val ss = ServerSocket(p)
                serverSocket = ss
                boundPort = ss.localPort
                break
            } catch (_: Exception) {}
        }
        if (serverSocket == null) {
            stopServer()
            return
        }

        thread(start = true, isDaemon = true) {
            try {
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.soTimeout = 30000
                    activeSockets.add(clientSocket)
                    val executor = clientExecutor
                    if (executor != null && !executor.isShutdown) {
                        try {
                            executor.execute { handleClient(clientSocket, pin, onPayloadReceived) }
                        } catch (_: java.util.concurrent.RejectedExecutionException) {
                            activeSockets.remove(clientSocket)
                            clientSocket.close()
                        }
                    } else {
                        activeSockets.remove(clientSocket)
                        clientSocket.close()
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

                // 1. Read Client Hello (ECDH Public Key)
                val clientHelloLine = readBoundedLine(reader) ?: return
                val clientPublicKey = SyncPairingSession.parseHelloMessage(clientHelloLine)

                // 2. Generate Server Keypair and Send Hello
                val serverKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                writer.println(SyncPairingSession.createHelloMessage(serverKeyPair))
                writer.flush()

                // 3. Derive Session Keys (256-bit ECDH)
                val sessionKeys = SyncPairingSession.deriveSessionKeys(
                    serverKeyPair.privateKey,
                    serverKeyPair.publicKey,
                    clientPublicKey
                )

                // 4. Verify Client Auth Commitment
                val clientAuthLine = readBoundedLine(reader) ?: return
                if (!SyncPairingSession.verifyAuthTag(clientAuthLine, sessionKeys.clientToServerMacKey, pin)) {
                    writer.println("ERROR:AuthFailed")
                    writer.flush()
                    return
                }

                // 5. Send Server Auth OK
                writer.println(SyncPairingSession.createAuthOkTag(sessionKeys.serverToClientMacKey, pin))
                writer.flush()

                // 6. Read and Decrypt Payload using C2S keys
                val incomingLine = readBoundedLine(reader) ?: return
                val decryptedJson = SyncPairingSession.decryptWithKeys(
                    message = incomingLine,
                    aesKey = sessionKeys.clientToServerAesKey,
                    macKey = sessionKeys.clientToServerMacKey
                )

                // 7. Process Application Vault Changes
                val chosenSecret = pairingCredentialFromRequest(decryptedJson, pin, korlibs.crypto.encoding.Hex.encode(sessionKeys.longTermClusterSecret))
                val responseJson = pairingResponse(onPayloadReceived(decryptedJson), pin, chosenSecret)

                // 8. Encrypt and Send Response using S2C keys
                val encryptedResponse = SyncPairingSession.encryptWithKeys(
                    plainText = responseJson,
                    aesKey = sessionKeys.serverToClientAesKey,
                    macKey = sessionKeys.serverToClientMacKey
                )
                writer.println(encryptedResponse)
                writer.flush()
                if (isInteractivePairing(pin)) stopServer()
                Logger.d(TAG, "Client request processed successfully (E2E ECDH SAS)")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling client sync request", e)
            try {
                PrintWriter(socket.getOutputStream(), true).apply {
                    println("ERROR:Failed")
                    flush()
                }
            } catch (_: Exception) {}
        } finally {
            activeSockets.remove(socket)
            try { socket.close() } catch (_: Exception) {}
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
        try {
            clientExecutor?.shutdownNow()
        } catch (_: Exception) {
        } finally {
            clientExecutor = null
        }
        activeSockets.forEach { try { it.close() } catch (_: Exception) {} }
        activeSockets.clear()
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

                // 3. Derive Session Keys (256-bit ECDH)
                val sessionKeys = SyncPairingSession.deriveSessionKeys(
                    clientKeyPair.privateKey,
                    clientKeyPair.publicKey,
                    serverPublicKey
                )

                // 4. Send Client Auth Tag
                writer.println(SyncPairingSession.createAuthTag(sessionKeys.clientToServerMacKey, pin))
                writer.flush()

                // 5. Read Server Auth OK
                val serverAuthOkLine = readBoundedLine(reader)
                    ?: throw IllegalStateException("Target device closed connection during auth")
                if (serverAuthOkLine.startsWith("ERROR:")) {
                    throw IllegalArgumentException("Authentication failed (PIN mismatch or MITM)")
                }
                if (!SyncPairingSession.verifyAuthOkTag(serverAuthOkLine, sessionKeys.serverToClientMacKey, pin)) {
                    throw IllegalArgumentException("Server authentication verification failed (potential MITM)")
                }

                // 6. Encrypt and Send Vault Payload using C2S keys
                onProgress?.invoke("Encrypting vault data...")
                val encryptedPayload = SyncPairingSession.encryptWithKeys(
                    plainText = pairingRequest(localPayloadJson, pin),
                    aesKey = sessionKeys.clientToServerAesKey,
                    macKey = sessionKeys.clientToServerMacKey
                )
                val payloadKb = encryptedPayload.length / 1024
                onProgress?.invoke("Transferring vault data ($payloadKb KB)...")
                writer.println(encryptedPayload)
                writer.flush()

                // 7. Wait for Encrypted Response
                onProgress?.invoke("Waiting for remote device to process changes...")
                val responseLine = readBoundedLine(reader)
                    ?: throw IllegalStateException("No response received from target device (connection closed)")

                if (responseLine.startsWith("ERROR:")) {
                    throw IllegalArgumentException("Target device rejected connection: $responseLine")
                }

                // 8. Decrypt and Authenticate Response using S2C keys
                onProgress?.invoke("Decrypting response...")
                receivePairingResponse(SyncPairingSession.decryptWithKeys(
                    message = responseLine,
                    aesKey = sessionKeys.serverToClientAesKey,
                    macKey = sessionKeys.serverToClientMacKey
                ), pin)
            }
        } catch (e: Exception) {
            throw e
        }
    }
}
