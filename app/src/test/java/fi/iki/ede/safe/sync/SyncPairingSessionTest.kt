package fi.iki.ede.safe.sync

import fi.iki.ede.safe.ui.sync.SyncPairingSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncPairingSessionTest {

    @Test
    fun test8DigitPinGenerationAndFormatting() {
        val pin = SyncPairingSession.generate8DigitPin()
        assertEquals(8, pin.length)
        assertTrue(pin.all { it.isDigit() })

        val formatted = SyncPairingSession.formatPinForDisplay(pin)
        assertEquals(11, formatted.length) // "XXXX - YYYY"
        assertTrue(formatted.contains(" - "))

        val normalized = SyncPairingSession.normalizePinInput(" 4920 - 8173 ")
        assertEquals("49208173", normalized)
    }

    @Test
    fun testPayloadEncryptionAndDecryptionWithPin() {
        val samplePin = "49208173"
        val originalPayload = """{"accounts":[{"email":"user@test.com","items":[{"title":"https://www.amazon.com/ap/signin"}]}]}"""

        val cipherHex = SyncPairingSession.encryptPayload(originalPayload, samplePin)
        assertNotEquals(originalPayload, cipherHex)
        assertTrue(cipherHex.isNotEmpty())

        val decryptedPayload = SyncPairingSession.decryptPayload(cipherHex, samplePin)
        assertEquals(originalPayload, decryptedPayload)
    }
}
