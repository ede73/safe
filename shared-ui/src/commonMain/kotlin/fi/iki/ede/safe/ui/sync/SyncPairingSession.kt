package fi.iki.ede.safe.ui.sync

import korlibs.crypto.AES
import korlibs.crypto.HMAC
import korlibs.crypto.Padding
import korlibs.crypto.SecureRandom
import korlibs.crypto.encoding.Hex

object SyncPairingSession {

    const val PROTOCOL_HELLO_PREFIX = "SAFE_HELLO:"
    const val PROTOCOL_PAYLOAD_PREFIX = "SAFE_PAYLOAD:"

    /**
     * Generates a secure random 8-digit PIN string (e.g., "49208173").
     */
    fun generate8DigitPin(): String {
        val randomBytes = SecureRandom.nextBytes(4)
        var intVal = ((randomBytes[0].toInt() and 0xFF) shl 24) or
                     ((randomBytes[1].toInt() and 0xFF) shl 16) or
                     ((randomBytes[2].toInt() and 0xFF) shl 8) or
                     (randomBytes[3].toInt() and 0xFF)
        intVal = intVal and 0x7FFFFFFF // ensure positive
        intVal %= 100_000_000 // limit to 0‑99,999,999
        return intVal.toString().padStart(8, '0')
    }

    /**
     * Formats an 8-digit PIN for human display: "4920 - 8173".
     */
    fun formatPinForDisplay(pin: String): String {
        val cleanPin = pin.filter { it.isDigit() }.padStart(8, '0').take(8)
        return "${cleanPin.substring(0, 4)} - ${cleanPin.substring(4, 8)}"
    }

    /**
     * Normalizes user pin input by stripping spaces and non-digits.
     */
    fun normalizePinInput(rawInput: String): String {
        return rawInput.filter { it.isDigit() }.take(8)
    }

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
     * Derives session AES and MAC keys using Ephemeral Curve25519 (X25519) ECDH shared secret
     * cryptographically bound to the user-verified PIN and both public keys.
     *
     * This guarantees that:
     * 1. Passive network eavesdroppers cannot perform offline PIN brute-forcing (requires breaking ECDH 2^128).
     * 2. Active MITM attackers cannot forge or substitute keys without knowing the out-of-band PIN.
     */
    fun deriveSessionKeys(
        myPrivateKey: ByteArray,
        myPublicKey: ByteArray,
        peerPublicKey: ByteArray,
        pin: String
    ): Pair<ByteArray, ByteArray> {
        val cleanPin = if (pin.any { !it.isDigit() } || pin.length != 8) pin.trim() else normalizePinInput(pin)
        val sharedSecret = X25519.scalarmult(myPrivateKey, peerPublicKey)
        require(!X25519.isAllZero(sharedSecret)) { "ECDH key agreement failed: peer public key is invalid or small-order" }

        val myPubHex = Hex.encode(myPublicKey)
        val peerPubHex = Hex.encode(peerPublicKey)
        val (pk1, pk2) = if (myPubHex < peerPubHex) {
            myPublicKey to peerPublicKey
        } else {
            peerPublicKey to myPublicKey
        }

        val kdfSalt = "SafeP2P-v2:".encodeToByteArray() + cleanPin.encodeToByteArray() + pk1 + pk2
        val masterKey = HMAC.hmacSHA256(sharedSecret, kdfSalt).bytes
        val macKey = HMAC.hmacSHA256(masterKey, "SafeP2P-v2-Mac".encodeToByteArray()).bytes
        return masterKey to macKey
    }

    /**
     * Encrypts plaintext JSON payload with Ephemeral ECDH + PIN transcript-bound AES-CBC-256 and HMAC-SHA256.
     */
    fun encryptPayload(
        plainJson: String,
        pin: String,
        myKeyPair: KeyPair,
        peerPublicKey: ByteArray
    ): String {
        val (masterKey, macKey) = deriveSessionKeys(myKeyPair.privateKey, myKeyPair.publicKey, peerPublicKey, pin)
        val iv = SecureRandom.nextBytes(16)
        val encryptedBytes = AES.encryptAesCbc(plainJson.encodeToByteArray(), masterKey, iv, Padding.PKCS7Padding)
        val ivAndCiphertext = iv + encryptedBytes
        val hmacTag = HMAC.hmacSHA256(macKey, ivAndCiphertext).bytes
        val combined = hmacTag + ivAndCiphertext
        return "$PROTOCOL_PAYLOAD_PREFIX${Hex.encode(combined)}"
    }

    /**
     * Decrypts ciphertext back into plaintext JSON after verifying HMAC-SHA256 payload integrity.
     */
    fun decryptPayload(
        message: String,
        pin: String,
        myKeyPair: KeyPair,
        peerPublicKey: ByteArray
    ): String {
        val cipherHex = if (message.startsWith(PROTOCOL_PAYLOAD_PREFIX)) {
            message.removePrefix(PROTOCOL_PAYLOAD_PREFIX).trim()
        } else {
            message.trim()
        }
        val (masterKey, macKey) = deriveSessionKeys(myKeyPair.privateKey, myKeyPair.publicKey, peerPublicKey, pin)
        val bytes = Hex.decode(cipherHex)
        require(bytes.size > 48) { "Invalid payload length: payload must include HMAC tag (32b) and IV (16b)" }

        val hmacTag = bytes.copyOfRange(0, 32)
        val ivAndCiphertext = bytes.copyOfRange(32, bytes.size)
        val expectedHmacTag = HMAC.hmacSHA256(macKey, ivAndCiphertext).bytes
        require(constantTimeEquals(hmacTag, expectedHmacTag)) { "Payload authentication failed: HMAC mismatch (incorrect PIN or tampering)" }

        val iv = ivAndCiphertext.copyOfRange(0, 16)
        val ciphertext = ivAndCiphertext.copyOfRange(16, ivAndCiphertext.size)
        val decryptedBytes = AES.decryptAesCbc(ciphertext, masterKey, iv, Padding.PKCS7Padding)
        return decryptedBytes.decodeToString()
    }

    /**
     * Constant-time byte array equality check to eliminate timing side-channels in cryptographic comparisons.
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
