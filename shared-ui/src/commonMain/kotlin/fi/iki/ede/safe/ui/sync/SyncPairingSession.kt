package fi.iki.ede.safe.ui.sync

import korlibs.crypto.AES
import korlibs.crypto.HMAC
import korlibs.crypto.Padding
import korlibs.crypto.SecureRandom
import korlibs.crypto.encoding.Hex

object SyncPairingSession {

    const val PROTOCOL_HELLO_PREFIX = "SAFE_HELLO:"
    const val PROTOCOL_AUTH_PREFIX = "SAFE_AUTH:"
    const val PROTOCOL_AUTH_OK_PREFIX = "SAFE_AUTH_OK:"
    const val PROTOCOL_PAYLOAD_PREFIX = "SAFE_PAYLOAD:"

    data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
        val publicKeyHex: String get() = Hex.encode(publicKey)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPair) return false
            return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int {
            var result = privateKey.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }

    fun generateEphemeralKeyPair(): KeyPair {
        val priv = X25519.generatePrivateKey()
        val pub = X25519.publicFromPrivate(priv)
        return KeyPair(priv, pub)
    }

    fun createHelloMessage(myKeyPair: KeyPair): String =
        "$PROTOCOL_HELLO_PREFIX${myKeyPair.publicKeyHex}"

    fun parseHelloMessage(line: String): ByteArray {
        require(line.startsWith(PROTOCOL_HELLO_PREFIX)) { "Invalid handshake message: expected $PROTOCOL_HELLO_PREFIX" }
        val hex = line.removePrefix(PROTOCOL_HELLO_PREFIX).trim()
        val pub = Hex.decode(hex)
        require(pub.size == 32) { "Invalid peer public key size: expected 32 bytes, got ${pub.size}" }
        return pub
    }

    /**
     * Session Keys derived using HKDF from Ephemeral X25519 ECDH shared secret.
     * Contains direction-specific 256-bit AES and MAC keys, long-term cluster secret, and 8-digit SAS code.
     */
    data class SessionKeys(
        val clientToServerAesKey: ByteArray,
        val clientToServerMacKey: ByteArray,
        val serverToClientAesKey: ByteArray,
        val serverToClientMacKey: ByteArray,
        val longTermClusterSecret: ByteArray,
        val sasCode: String
    )

    /**
     * Derives session keys purely from 256-bit X25519 ECDH shared secret.
     * The keys have full 128-bit/256-bit cryptographic strength and DO NOT depend on the PIN for encryption.
     */
    fun deriveSessionKeys(
        myPrivateKey: ByteArray,
        myPublicKey: ByteArray,
        peerPublicKey: ByteArray
    ): SessionKeys {
        val sharedSecret = X25519.scalarmult(myPrivateKey, peerPublicKey)
        require(!X25519.isAllZero(sharedSecret)) { "ECDH key agreement failed: peer public key is invalid or small-order" }

        val myPubHex = Hex.encode(myPublicKey)
        val peerPubHex = Hex.encode(peerPublicKey)
        val (pk1, pk2) = if (myPubHex < peerPubHex) {
            myPublicKey to peerPublicKey
        } else {
            peerPublicKey to myPublicKey
        }

        // HKDF-Extract: PRK = HMAC-SHA256(salt = pk1 || pk2, IKM = sharedSecret)
        val salt = "SafeP2P-v3-Salt:".encodeToByteArray() + pk1 + pk2
        val prk = HMAC.hmacSHA256(salt, sharedSecret).bytes

        // HKDF-Expand for directional keys and secrets
        val c2sAes = HMAC.hmacSHA256(prk, "SafeP2P-v3-C2S-AES".encodeToByteArray()).bytes
        val c2sMac = HMAC.hmacSHA256(prk, "SafeP2P-v3-C2S-MAC".encodeToByteArray()).bytes
        val s2cAes = HMAC.hmacSHA256(prk, "SafeP2P-v3-S2C-AES".encodeToByteArray()).bytes
        val s2cMac = HMAC.hmacSHA256(prk, "SafeP2P-v3-S2C-MAC".encodeToByteArray()).bytes
        val clusterSecret = HMAC.hmacSHA256(prk, "SafeP2P-v3-ClusterKey".encodeToByteArray()).bytes

        // Compute 8-digit SAS verification code:
        val sasHash = HMAC.hmacSHA256(prk, "SafeP2P-v3-SAS".encodeToByteArray()).bytes
        var intVal = ((sasHash[0].toInt() and 0xFF) shl 24) or
                ((sasHash[1].toInt() and 0xFF) shl 16) or
                ((sasHash[2].toInt() and 0xFF) shl 8) or
                (sasHash[3].toInt() and 0xFF)
        intVal = (intVal and 0x7FFFFFFF) % 100_000_000
        val sasCode = intVal.toString().padStart(8, '0')

        return SessionKeys(
            clientToServerAesKey = c2sAes,
            clientToServerMacKey = c2sMac,
            serverToClientAesKey = s2cAes,
            serverToClientMacKey = s2cMac,
            longTermClusterSecret = clusterSecret,
            sasCode = sasCode
        )
    }

    /** A 96-bit out-of-band secret. Grouping is only for display. */
    const val PAIRING_CODE_LENGTH = 24

    fun formatPinForDisplay(pin: String): String =
        normalizePinInput(pin).chunked(4).joinToString(" - ")

    /**
     * Normalizes user input without silently accepting invalid characters.
     */
    fun normalizePinInput(rawInput: String): String {
        return if (rawInput.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it.isWhitespace() || it == '-' }) {
            rawInput.filterNot { it.isWhitespace() || it == '-' }.lowercase()
        } else rawInput.trim().lowercase()
    }

    /**
     * Generates a 96-bit pairing secret. Short decimal PINs permit offline
     * guessing by an active attacker who intercepts the authentication tag.
     */
    fun generatePairingCode(): String = Hex.encode(SecureRandom.nextBytes(12))

    /**
     * Creates authentication commitment tag: HMAC(macKey, "SAFE_AUTH:" + pinOrSas).
     */
    fun createAuthTag(macKey: ByteArray, pinOrSas: String): String {
        val clean = normalizePinInput(pinOrSas)
        val tag = HMAC.hmacSHA256(macKey, "SAFE_AUTH:$clean".encodeToByteArray()).bytes
        return "$PROTOCOL_AUTH_PREFIX${Hex.encode(tag)}"
    }

    /**
     * Verifies authentication commitment tag in constant time.
     */
    fun verifyAuthTag(line: String, macKey: ByteArray, pinOrSas: String): Boolean {
        if (!line.startsWith(PROTOCOL_AUTH_PREFIX)) return false
        val receivedHex = line.removePrefix(PROTOCOL_AUTH_PREFIX).trim()
        if (receivedHex.length != 64) return false
        val receivedTag = try { Hex.decode(receivedHex) } catch (_: Throwable) { return false }
        if (receivedTag.size != 32) return false
        val clean = normalizePinInput(pinOrSas)
        val expectedTag = HMAC.hmacSHA256(macKey, "SAFE_AUTH:$clean".encodeToByteArray()).bytes
        return constantTimeEquals(receivedTag, expectedTag)
    }

    fun createAuthOkTag(macKey: ByteArray, pinOrSas: String): String {
        val clean = normalizePinInput(pinOrSas)
        val tag = HMAC.hmacSHA256(macKey, "SAFE_AUTH_OK:$clean".encodeToByteArray()).bytes
        return "$PROTOCOL_AUTH_OK_PREFIX${Hex.encode(tag)}"
    }

    fun verifyAuthOkTag(line: String, macKey: ByteArray, pinOrSas: String): Boolean {
        if (!line.startsWith(PROTOCOL_AUTH_OK_PREFIX)) return false
        val receivedHex = line.removePrefix(PROTOCOL_AUTH_OK_PREFIX).trim()
        if (receivedHex.length != 64) return false
        val receivedTag = try { Hex.decode(receivedHex) } catch (_: Throwable) { return false }
        if (receivedTag.size != 32) return false
        val clean = normalizePinInput(pinOrSas)
        val expectedTag = HMAC.hmacSHA256(macKey, "SAFE_AUTH_OK:$clean".encodeToByteArray()).bytes
        return constantTimeEquals(receivedTag, expectedTag)
    }

    /**
     * Encrypts plaintext payload using directional AES key and HMAC tag.
     */
    fun encryptWithKeys(plainText: String, aesKey: ByteArray, macKey: ByteArray): String {
        val iv = SecureRandom.nextBytes(16)
        val cipherBytes = AES.encryptAesCbc(plainText.encodeToByteArray(), aesKey, iv, Padding.PKCS7Padding)
        val ivAndCipher = iv + cipherBytes
        val hmac = HMAC.hmacSHA256(macKey, ivAndCipher).bytes
        return "$PROTOCOL_PAYLOAD_PREFIX${Hex.encode(hmac + ivAndCipher)}"
    }

    /**
     * Decrypts ciphertext payload using directional AES key after verifying HMAC tag.
     */
    fun decryptWithKeys(message: String, aesKey: ByteArray, macKey: ByteArray): String {
        val hex = if (message.startsWith(PROTOCOL_PAYLOAD_PREFIX)) {
            message.removePrefix(PROTOCOL_PAYLOAD_PREFIX).trim()
        } else {
            message.trim()
        }
        val bytes = Hex.decode(hex)
        require(bytes.size > 48) { "Invalid payload length: must include HMAC tag (32b) and IV (16b)" }
        val hmacTag = bytes.copyOfRange(0, 32)
        val ivAndCipher = bytes.copyOfRange(32, bytes.size)
        val expectedHmac = HMAC.hmacSHA256(macKey, ivAndCipher).bytes
        require(constantTimeEquals(hmacTag, expectedHmac)) { "Payload HMAC verification failed" }

        val iv = ivAndCipher.copyOfRange(0, 16)
        val cipher = ivAndCipher.copyOfRange(16, ivAndCipher.size)
        val decrypted = AES.decryptAesCbc(cipher, aesKey, iv, Padding.PKCS7Padding)
        return decrypted.decodeToString()
    }

    /**
     * Constant-time byte array equality check to eliminate timing side-channels.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
