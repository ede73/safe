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
                // Check if port is already taken by an active service
                val testSock = socket(AF_INET, SOCK_STREAM, 0)
                if (testSock >= 0) {
                    val testAddr = alloc<sockaddr_in>()
                    memset(testAddr.ptr, 0, sizeOf<sockaddr_in>().toULong())
                    testAddr.sin_len = sizeOf<sockaddr_in>().toUByte()
                    testAddr.sin_family = AF_INET.toUByte()
                    testAddr.sin_port = htons(p.toUShort())
                    testAddr.sin_addr.s_addr = parseIpv4ToNetworkOrder("127.0.0.1") ?: 0x0100007Fu
                    val isTaken = connect(testSock, testAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) == 0
                    close(testSock)
                    if (isTaken) return@memScoped
                }

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
                        return@memScoped
                    } else {
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

                memScoped {
                    val incomingLine = readLineFromFd(clientFd)
                    if (!incomingLine.isNullOrEmpty()) {
                        try {
                            val decryptedJson = SyncPairingSession.decryptPayload(incomingLine, pin)
                            val responseJson = onPayloadReceived(decryptedJson)
                            val encryptedResponse = SyncPairingSession.encryptPayload(responseJson, pin) + "\n"
                            sendAll(clientFd, encryptedResponse.encodeToByteArray())
                        } catch (e: Exception) {
                            val errStr = "ERROR:${e::class.simpleName}:${e.message}\n"
                            sendAll(clientFd, errStr.encodeToByteArray())
                        }
                    }
                    shutdown(clientFd, SHUT_WR)
                    close(clientFd)
                }
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
        onProgress?.invoke("Encrypting vault data...")
        val encryptedPayload = SyncPairingSession.encryptPayload(localPayloadJson, pin) + "\n"
        val requestBytes = encryptedPayload.encodeToByteArray()
        onProgress?.invoke("Connecting to $hostAddress:$port...")

        memScoped {
            val sock = socket(AF_INET, SOCK_STREAM, 0)
            if (sock < 0) throw IllegalStateException("Failed to create client socket")

            val tv = alloc<timeval>()
            tv.tv_sec = 60
            tv.tv_usec = 0
            setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().toUInt())

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
                    serverAddr.sin_addr.s_addr = 0x0100007Fu
                }
            }

            if (connect(sock, serverAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) < 0) {
                close(sock)
                throw IllegalStateException("Failed to connect to $hostAddress:$port")
            }

            sendAll(sock, requestBytes)

            val responseLine = readLineFromFd(sock)
            close(sock)

            if (responseLine.isNullOrEmpty()) {
                throw IllegalStateException("No response from target device")
            }

            if (responseLine.startsWith("ERROR:")) {
                throw IllegalArgumentException("Target device rejected connection: $responseLine")
            }

            SyncPairingSession.decryptPayload(responseLine, pin)
        }
    }
}
