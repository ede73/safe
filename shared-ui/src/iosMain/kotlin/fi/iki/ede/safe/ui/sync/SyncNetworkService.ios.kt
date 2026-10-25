@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package fi.iki.ede.safe.ui.sync

import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences
import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.Foundation.NSLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlin.concurrent.Volatile

private const val TAG = "SyncNetworkServiceIOS"

actual class SyncNetworkService {
    private var serverFd: Int = -1
    private val clientSlots = Semaphore(4)
    private val clientLock = NSLock()
    private val activeClientFds = mutableSetOf<Int>()
    @Volatile private var isRunning = false
    actual var boundPort: Int = -1
    actual val isAndroidGuest: Boolean = false

    private fun htons(v: UShort): UShort = ((v.toInt() and 0xFF) shl 8 or (v.toInt() ushr 8)).toUShort()

    private fun parseIpv4ToNetworkOrder(ipStr: String): UInt? {
        val cleanIp = if (ipStr.equals("localhost", ignoreCase = true)) "127.0.0.1" else ipStr
        val parts = cleanIp.split(".")
        if (parts.size != 4) return null
        val b0 = parts[0].toUIntOrNull() ?: return null
        val b1 = parts[1].toUIntOrNull() ?: return null
        val b2 = parts[2].toUIntOrNull() ?: return null
        val b3 = parts[3].toUIntOrNull() ?: return null
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun sendAll(fd: Int, data: ByteArray): Boolean {
        var totalSent = 0
        val totalSize = data.size
        data.usePinned { pinned ->
            while (totalSent < totalSize) {
                val sent = send(fd, pinned.addressOf(totalSent), (totalSize - totalSent).toULong(), 0)
                if (sent <= 0) return false
                totalSent += sent.toInt()
            }
        }
        return true
    }

    private fun closeTrackedClient(fd: Int) {
        clientLock.lock()
        val owned = activeClientFds.remove(fd)
        clientLock.unlock()
        if (owned) {
            shutdown(fd, SHUT_RDWR)
            close(fd)
        }
    }

    private fun readLineFromFd(fd: Int, pending: ArrayDeque<Byte>): String? {
        var buffer = ByteArray(8192)
        var count = 0
        val tempBuf = ByteArray(4096)
        while (true) {
            while (pending.isNotEmpty()) {
                val byte = pending.removeFirst()
                if (byte == '\n'.code.toByte()) {
                    return buffer.decodeToString(0, count).removeSuffix("\r")
                }
                if (count >= MAX_SYNC_PAYLOAD_BYTES) {
                    throw IllegalStateException("Sync payload exceeded maximum limit of $MAX_SYNC_PAYLOAD_BYTES bytes")
                }
                if (count == buffer.size) {
                    buffer = buffer.copyOf((buffer.size * 2).coerceAtMost(MAX_SYNC_PAYLOAD_BYTES))
                }
                buffer[count++] = byte
            }
            val bytesRead = tempBuf.usePinned { pinned ->
                recv(fd, pinned.addressOf(0), tempBuf.size.toULong(), 0)
            }
            if (bytesRead <= 0) return if (count > 0) buffer.decodeToString(0, count) else null
            for (i in 0 until bytesRead.toInt()) pending.addLast(tempBuf[i])
        }
    }

    actual fun startServer(
        port: Int,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    ) {
        stopServer()
        isRunning = true
        val candidatePorts = if (port == 0) listOf(58446, 58447, 58448, 58449, 58450) else listOf(port, port + 1, port + 2, port + 3, port + 4, port + 5, port + 6, port + 7)
        for (p in candidatePorts) {
            memScoped {
                val fd = socket(AF_INET, SOCK_STREAM, 0)
                if (fd >= 0) {
                    val reuse = alloc<IntVar>()
                    reuse.value = 1
                    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<IntVar>().toUInt())

                    val serverAddr = alloc<sockaddr_in>()
                    memset(serverAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                    serverAddr.sin_len = sizeOf<sockaddr_in>().toUByte()
                    serverAddr.sin_family = AF_INET.toUByte()
                    serverAddr.sin_addr.s_addr = 0u
                    serverAddr.sin_port = htons(p.toUShort())

                    if (bind(fd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0 && listen(fd, 5) == 0) {
                        serverFd = fd
                        boundPort = p
                        Logger.d(TAG, "Server socket bound to port $p")
                        return@memScoped
                    } else {
                        close(fd)
                    }
                }
            }
            if (serverFd >= 0) break
        }
        if (serverFd < 0) {
            stopServer()
            return
        }

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            while (isRunning) {
                val clientFd = accept(serverFd, null, null)
                if (clientFd < 0) break
                if (!clientSlots.tryAcquire()) {
                    close(clientFd)
                    continue
                }
                clientLock.lock()
                activeClientFds.add(clientFd)
                clientLock.unlock()

                dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                    try { handleClient(clientFd, pin, onPayloadReceived) }
                    finally { clientSlots.release() }
                }
            }
        }
    }

    private fun handleClient(
        clientFd: Int,
        pin: String,
        onPayloadReceived: (remotePayloadJson: String) -> String
    ) {
        memScoped {
            val pending = ArrayDeque<Byte>()
            try {
                val rcvTv = alloc<timeval>()
                rcvTv.tv_sec = 30
                rcvTv.tv_usec = 0
                setsockopt(clientFd, SOL_SOCKET, SO_RCVTIMEO, rcvTv.ptr, sizeOf<timeval>().toUInt())

                val sndTv = alloc<timeval>()
                sndTv.tv_sec = 10
                sndTv.tv_usec = 0
                setsockopt(clientFd, SOL_SOCKET, SO_SNDTIMEO, sndTv.ptr, sizeOf<timeval>().toUInt())

                // 1. Read Client Hello (ECDH Public Key)
                val clientHelloLine = readLineFromFd(clientFd, pending) ?: return@memScoped
                val clientPublicKey = SyncPairingSession.parseHelloMessage(clientHelloLine)

                // 2. Generate Server Keypair and Send Hello
                val serverKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                val serverHello = SyncPairingSession.createHelloMessage(serverKeyPair) + "\n"
                sendAll(clientFd, serverHello.encodeToByteArray())

                // 3. Derive Session Keys (256-bit ECDH)
                val sessionKeys = SyncPairingSession.deriveSessionKeys(
                    serverKeyPair.privateKey,
                    serverKeyPair.publicKey,
                    clientPublicKey
                )

                // 4. Verify Client Auth Commitment
                val clientAuthLine = readLineFromFd(clientFd, pending) ?: return@memScoped
                if (!SyncPairingSession.verifyAuthTag(clientAuthLine, sessionKeys.clientToServerMacKey, pin)) {
                    sendAll(clientFd, "ERROR:AuthFailed\n".encodeToByteArray())
                    return@memScoped
                }

                // 5. Send Server Auth OK
                val serverAuthOk = SyncPairingSession.createAuthOkTag(sessionKeys.serverToClientMacKey, pin) + "\n"
                sendAll(clientFd, serverAuthOk.encodeToByteArray())

                // 6. Read and Decrypt Payload using C2S keys
                val incomingPayloadLine = readLineFromFd(clientFd, pending) ?: return@memScoped
                val decryptedJson = SyncPairingSession.decryptWithKeys(
                    message = incomingPayloadLine,
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
                ) + "\n"
                sendAll(clientFd, encryptedResponse.encodeToByteArray())
                if (isInteractivePairing(pin)) stopServer()
                Logger.d(TAG, "Client request processed successfully (E2E ECDH SAS)")
            } catch (t: Throwable) {
                Logger.e(TAG, "Error handling client sync request", t)
                try {
                    sendAll(clientFd, "ERROR:Failed\n".encodeToByteArray())
                } catch (_: Throwable) {}
            } finally {
                closeTrackedClient(clientFd)
            }
        }
    }

    actual fun stopServer() {
        isRunning = false
        boundPort = -1
        if (serverFd >= 0) {
            close(serverFd)
            serverFd = -1
        }
        clientLock.lock()
        val clients = activeClientFds.toList()
        activeClientFds.clear()
        clientLock.unlock()
        clients.forEach { fd ->
            shutdown(fd, SHUT_RDWR)
            close(fd)
        }
    }

    actual suspend fun sendPayloadToHost(
        hostAddress: String,
        port: Int,
        pin: String,
        localPayloadJson: String,
        onProgress: ((String) -> Unit)?
    ): String = withContext(Dispatchers.Default) {
        onProgress?.invoke("Connecting to $hostAddress:$port...")

        memScoped {
            val sock = socket(AF_INET, SOCK_STREAM, 0)
            if (sock < 0) throw IllegalStateException("Failed to create client socket")

            try {
                val pending = ArrayDeque<Byte>()
                val tv = alloc<timeval>()
                tv.tv_sec = 60
                tv.tv_usec = 0
                setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().toUInt())

                val sndTv = alloc<timeval>()
                sndTv.tv_sec = 10
                sndTv.tv_usec = 0
                setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, sndTv.ptr, sizeOf<timeval>().toUInt())

                val serverAddr = alloc<sockaddr_in>()
                memset(serverAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                serverAddr.sin_len = sizeOf<sockaddr_in>().toUByte()
                serverAddr.sin_family = AF_INET.toUByte()
                serverAddr.sin_port = htons(port.toUShort())
                val directIpv4 = parseIpv4ToNetworkOrder(hostAddress)
                if (directIpv4 != null) {
                    serverAddr.sin_addr.s_addr = directIpv4
                } else {
                    val hints = alloc<addrinfo>()
                    memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
                    hints.ai_family = AF_INET
                    hints.ai_socktype = SOCK_STREAM
                    val res = allocPointerTo<addrinfo>()
                    if (getaddrinfo(hostAddress, null, hints.ptr, res.ptr) == 0 && res.value != null) {
                        val resolved = res.value!!.pointed.ai_addr?.reinterpret<sockaddr_in>()
                        if (resolved != null) {
                            serverAddr.sin_addr.s_addr = resolved.pointed.sin_addr.s_addr
                        }
                        freeaddrinfo(res.value)
                    } else {
                        throw IllegalStateException("Could not resolve host address: $hostAddress")
                    }
                }

                if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) < 0) {
                    throw IllegalStateException("Failed to connect to $hostAddress:$port")
                }

                // 1. Generate Client Ephemeral Keypair and Send Hello
                val clientKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                val clientHello = SyncPairingSession.createHelloMessage(clientKeyPair) + "\n"
                sendAll(sock, clientHello.encodeToByteArray())

                // 2. Read Server Hello (ECDH Public Key)
                onProgress?.invoke("Negotiating ECDH key agreement...")
                val serverHelloLine = readLineFromFd(sock, pending)
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
                val clientAuth = SyncPairingSession.createAuthTag(sessionKeys.clientToServerMacKey, pin) + "\n"
                sendAll(sock, clientAuth.encodeToByteArray())

                // 5. Read Server Auth OK
                val serverAuthOkLine = readLineFromFd(sock, pending)
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
                ) + "\n"
                val payloadKb = encryptedPayload.length / 1024
                onProgress?.invoke("Transferring vault data ($payloadKb KB)...")
                sendAll(sock, encryptedPayload.encodeToByteArray())

                // 7. Wait for Response
                onProgress?.invoke("Waiting for remote device to process changes...")
                val responseLine = readLineFromFd(sock, pending)
                    ?: throw IllegalStateException("No response from target device")

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
            } finally {
                close(sock)
            }
        }
    }
}
