package fi.iki.ede.safe.ui.sync

import korlibs.crypto.AES
import korlibs.crypto.Padding
import korlibs.crypto.SHA256
import korlibs.crypto.SecureRandom
import korlibs.crypto.encoding.Hex

object SyncPairingSession {

    private const val SALT_STRING = "SafeLocalDeviceSyncSaltV1"

    /**
     * Generates a secure random 8-digit PIN string (e.g., "49208173").
     */
    fun generate8DigitPin(): String {
        val randomInt = SecureRandom.nextInt(100_000_000)
        return randomInt.toString().padStart(8, '0')
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
     * Derives a 256-bit AES key from an 8-digit PIN using SHA-256 stretching.
     */
    fun deriveAesKeyFromPin(pin: String): ByteArray {
        val cleanPin = normalizePinInput(pin)
        val combined = "$cleanPin:$SALT_STRING"
        var hash = SHA256.digest(combined.encodeToByteArray()).bytes
        // Perform 5,000 rounds of SHA-256 stretching for local pairing security
        for (i in 0 until 5000) {
            hash = SHA256.digest(hash + cleanPin.encodeToByteArray()).bytes
        }
        return hash
    }

    /**
     * Encrypts plaintext JSON payload with the derived AES key.
     */
    fun encryptPayload(plainJson: String, pin: String): String {
        val key = deriveAesKeyFromPin(pin)
        val iv = SecureRandom.nextBytes(16)
        val encryptedBytes = AES.encryptAesCbc(plainJson.encodeToByteArray(), key, iv, Padding.PKCS7Padding)
        val combined = iv + encryptedBytes
        return Hex.encode(combined)
    }

    /**
     * Decrypts ciphertext back into plaintext JSON using the derived AES key.
     */
    fun decryptPayload(cipherHex: String, pin: String): String {
        val key = deriveAesKeyFromPin(pin)
        val bytes = Hex.decode(cipherHex)
        require(bytes.size > 16) { "Invalid payload length" }
        val iv = bytes.copyOfRange(0, 16)
        val ciphertext = bytes.copyOfRange(16, bytes.size)
        val decryptedBytes = AES.decryptAesCbc(ciphertext, key, iv, Padding.PKCS7Padding)
        return decryptedBytes.decodeToString()
    }
}
