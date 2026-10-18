package fi.iki.ede.safe.ui.sync

import kotlinx.cinterop.*
import platform.posix.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

@OptIn(ExperimentalForeignApi::class)
actual class SyncNetworkService {
    private var serverFd: Int = -1
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

    private fun readLineFromFd(fd: Int): String? {
        val accumulated = mutableListOf<Byte>()
        val tempBuf = ByteArray(4096)
        while (true) {
            val bytesRead = tempBuf.usePinned { pinned ->
                recv(fd, pinned.addressOf(0), tempBuf.size.toULong(), 0)
            }
            if (bytesRead <= 0) break
            for (i in 0 until bytesRead.toInt()) {
                val b = tempBuf[i]
                if (b == '\n'.code.toByte()) {
                    return accumulated.toByteArray().decodeToString().trim()
                }
                accumulated.add(b)
                if (accumulated.size > MAX_SYNC_PAYLOAD_BYTES) {
                    throw IllegalStateException("Sync payload exceeded maximum limit of $MAX_SYNC_PAYLOAD_BYTES bytes")
                }
            }
        }
        return if (accumulated.isNotEmpty()) accumulated.toByteArray().decodeToString().trim() else null
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
                    setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, reuse.ptr, sizeOf<IntVar>().toUInt())

                    val serverAddr = alloc<sockaddr_in>()
                    memset(serverAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                    serverAddr.sin_len = sizeOf<sockaddr_in>().toUByte()
                    serverAddr.sin_family = AF_INET.toUByte()
                    serverAddr.sin_addr.s_addr = 0u
                    serverAddr.sin_port = htons(p.toUShort())

                    if (bind(fd, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0 && listen(fd, 5) == 0) {
                        serverFd = fd
                        boundPort = p
                        fi.iki.ede.logger.Logger.d("SyncNetworkServiceIOS", "Server socket bound to port $p, fd=$fd")
                        fi.iki.ede.logger.firebaseLog("SafeSyncNetwork", "iOS Server socket bound to port $p, fd=$fd")
                        return@memScoped
                    } else {
                        fi.iki.ede.logger.Logger.w("SyncNetworkServiceIOS", "Failed to bind to port $p: errno=$errno")
                        close(fd)
                    }
                }
            }
            if (serverFd >= 0) break
        }
        if (serverFd < 0) return

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            while (isRunning) {
                val clientFd = accept(serverFd, null, null)
                if (clientFd < 0) break

                // Dispatch each client session to avoid blocking the accept loop
                dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                    handleClient(clientFd, pin, onPayloadReceived)
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
            try {
                // Set receive and send timeouts on client socket to prevent hanging
                val rcvTv = alloc<timeval>()
                rcvTv.tv_sec = 30
                rcvTv.tv_usec = 0
                setsockopt(clientFd, SOL_SOCKET, SO_RCVTIMEO, rcvTv.ptr, sizeOf<timeval>().toUInt())

                val sndTv = alloc<timeval>()
                sndTv.tv_sec = 10
                sndTv.tv_usec = 0
                setsockopt(clientFd, SOL_SOCKET, SO_SNDTIMEO, sndTv.ptr, sizeOf<timeval>().toUInt())

                // 1. Read Client Hello
                val clientHelloLine = readLineFromFd(clientFd) ?: return@memScoped
                val clientPublicKey = SyncPairingSession.parseHelloMessage(clientHelloLine)

                // 2. Generate Server Keypair and Send Hello
                val serverKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                val serverHello = SyncPairingSession.createHelloMessage(serverKeyPair) + "\n"
                sendAll(clientFd, serverHello.encodeToByteArray())

                // 3. Read and Decrypt Payload
                val incomingPayloadLine = readLineFromFd(clientFd) ?: return@memScoped
                val decryptedJson = SyncPairingSession.decryptPayload(
                    message = incomingPayloadLine,
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
                ) + "\n"
                sendAll(clientFd, encryptedResponse.encodeToByteArray())
            } catch (t: Throwable) {
                try {
                    sendAll(clientFd, "ERROR:Failed\n".encodeToByteArray())
                } catch (_: Throwable) {}
            } finally {
                shutdown(clientFd, SHUT_WR)
                close(clientFd)
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

                // 1. Send Client Hello
                val clientKeyPair = SyncPairingSession.generateEphemeralKeyPair()
                val clientHello = SyncPairingSession.createHelloMessage(clientKeyPair) + "\n"
                sendAll(sock, clientHello.encodeToByteArray())

                // 2. Read Server Hello
                onProgress?.invoke("Negotiating ECDH key agreement...")
                val serverHelloLine = readLineFromFd(sock)
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
                ) + "\n"
                val payloadKb = encryptedPayload.length / 1024
                onProgress?.invoke("Transferring vault data ($payloadKb KB)...")
                sendAll(sock, encryptedPayload.encodeToByteArray())

                // 4. Wait for Response
                onProgress?.invoke("Waiting for remote device to process changes...")
                val responseLine = readLineFromFd(sock)
                    ?: throw IllegalStateException("No response from target device")

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
            } finally {
                close(sock)
            }
        }
    }
}
