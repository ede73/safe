package fi.iki.ede.safe.ui.sync

import korlibs.crypto.AES
import korlibs.crypto.HMAC
import korlibs.crypto.Padding
import korlibs.crypto.SHA256
import korlibs.crypto.SecureRandom
import korlibs.crypto.encoding.Hex

object SyncPairingSession {

    private const val SALT_STRING = "SafeLocalDeviceSyncSaltV1"
    private const val MAC_INFO_STRING = "SafeLocalDeviceSyncMacV1"

    /**
     * Generates a secure random 8-digit PIN string (e.g., "49208173").
     */
    fun generate8DigitPin(): String {
        // Generate a cryptographically secure random 8‑digit PIN
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

    /**
     * Derives a 256-bit AES key from an 8-digit PIN or cluster secret using SHA-256 stretching.
     */
    fun deriveAesKeyFromPin(pin: String): ByteArray {
        val cleanPin = if (pin.any { !it.isDigit() } || pin.length != 8) pin.trim() else normalizePinInput(pin)
        val combined = "$cleanPin:$SALT_STRING"
        var hash = SHA256.digest(combined.encodeToByteArray()).bytes
        // Perform 5,000 rounds of SHA-256 stretching for local pairing security
        for (i in 0 until 5000) {
            hash = SHA256.digest(hash + cleanPin.encodeToByteArray()).bytes
        }
        return hash
    }

    private fun deriveMacKey(masterKey: ByteArray): ByteArray {
        return HMAC.hmacSHA256(masterKey, MAC_INFO_STRING.encodeToByteArray()).bytes
    }

    /**
     * Encrypts plaintext JSON payload with the derived AES key and HMAC-SHA256 integrity tag.
     */
    fun encryptPayload(plainJson: String, pin: String): String {
        val masterKey = deriveAesKeyFromPin(pin)
        val macKey = deriveMacKey(masterKey)
        val iv = SecureRandom.nextBytes(16)
        val encryptedBytes = AES.encryptAesCbc(plainJson.encodeToByteArray(), masterKey, iv, Padding.PKCS7Padding)
        val ivAndCiphertext = iv + encryptedBytes
        val hmacTag = HMAC.hmacSHA256(macKey, ivAndCiphertext).bytes
        val combined = hmacTag + ivAndCiphertext
        return Hex.encode(combined)
    }

    /**
     * Decrypts ciphertext back into plaintext JSON after verifying HMAC-SHA256 payload integrity.
     */
    fun decryptPayload(cipherHex: String, pin: String): String {
        val masterKey = deriveAesKeyFromPin(pin)
        val macKey = deriveMacKey(masterKey)
        val bytes = Hex.decode(cipherHex)
        require(bytes.size > 48) { "Invalid payload length: payload must include HMAC tag (32b) and IV (16b)" }
        val hmacTag = bytes.copyOfRange(0, 32)
        val ivAndCiphertext = bytes.copyOfRange(32, bytes.size)
        val expectedHmacTag = HMAC.hmacSHA256(macKey, ivAndCiphertext).bytes
        require(hmacTag.contentEquals(expectedHmacTag)) { "Payload integrity check failed: HMAC mismatch" }

        val iv = ivAndCiphertext.copyOfRange(0, 16)
        val ciphertext = ivAndCiphertext.copyOfRange(16, ivAndCiphertext.size)
        val decryptedBytes = AES.decryptAesCbc(ciphertext, masterKey, iv, Padding.PKCS7Padding)
        return decryptedBytes.decodeToString()
    }
}

