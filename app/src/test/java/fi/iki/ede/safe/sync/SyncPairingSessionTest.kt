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
    fun testEcdhKeyExchangeAndPinBoundPayloadEncryption() {
        val samplePin = "49208173"
        val originalPayload = """{"accounts":[{"email":"user@test.com","items":[{"title":"https://www.amazon.com/ap/signin"}]}]}"""

        // Client and Server generate ephemeral keypairs
        val clientKeys = SyncPairingSession.generateEphemeralKeyPair()
        val serverKeys = SyncPairingSession.generateEphemeralKeyPair()

        // 1. Hello exchange
        val clientHello = SyncPairingSession.createHelloMessage(clientKeys)
        val serverHello = SyncPairingSession.createHelloMessage(serverKeys)

        val clientPubReceivedByServer = SyncPairingSession.parseHelloMessage(clientHello)
        val serverPubReceivedByClient = SyncPairingSession.parseHelloMessage(serverHello)

        // 2. Client encrypts payload to Server using Server's public key + PIN
        val encryptedByClient = SyncPairingSession.encryptPayload(
            plainJson = originalPayload,
            pin = samplePin,
            myKeyPair = clientKeys,
            peerPublicKey = serverPubReceivedByClient
        )
        assertNotEquals(originalPayload, encryptedByClient)

        // 3. Server decrypts payload using Client's public key + PIN
        val decryptedByServer = SyncPairingSession.decryptPayload(
            message = encryptedByClient,
            pin = samplePin,
            myKeyPair = serverKeys,
            peerPublicKey = clientPubReceivedByServer
        )
        assertEquals(originalPayload, decryptedByServer)

        // 4. Server encrypts response back to Client
        val serverResponse = """{"status":"converged","items":[]}"""
        val encryptedByServer = SyncPairingSession.encryptPayload(
            plainJson = serverResponse,
            pin = samplePin,
            myKeyPair = serverKeys,
            peerPublicKey = clientPubReceivedByServer
        )

        // 5. Client decrypts response from Server
        val decryptedByClient = SyncPairingSession.decryptPayload(
            message = encryptedByServer,
            pin = samplePin,
            myKeyPair = clientKeys,
            peerPublicKey = serverPubReceivedByClient
        )
        assertEquals(serverResponse, decryptedByClient)
    }

    @Test
    fun testIncorrectPinFailsDecryption() {
        val correctPin = "49208173"
        val wrongPin = "11112222"
        val originalPayload = """{"test":"secret_vault_record"}"""

        val clientKeys = SyncPairingSession.generateEphemeralKeyPair()
        val serverKeys = SyncPairingSession.generateEphemeralKeyPair()

        val encryptedByClient = SyncPairingSession.encryptPayload(
            plainJson = originalPayload,
            pin = correctPin,
            myKeyPair = clientKeys,
            peerPublicKey = serverKeys.publicKey
        )

        // Attempting to decrypt with wrong PIN throws IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            SyncPairingSession.decryptPayload(
                message = encryptedByClient,
                pin = wrongPin,
                myKeyPair = serverKeys,
                peerPublicKey = clientKeys.publicKey
            )
        }
    }

    @Test
    fun testPayloadTamperingFailsHmacVerification() {
        val samplePin = "49208173"
        val originalPayload = """{"test":"tamper_proof_data"}"""

        val clientKeys = SyncPairingSession.generateEphemeralKeyPair()
        val serverKeys = SyncPairingSession.generateEphemeralKeyPair()

        val encrypted = SyncPairingSession.encryptPayload(
            plainJson = originalPayload,
            pin = samplePin,
            myKeyPair = clientKeys,
            peerPublicKey = serverKeys.publicKey
        )

        // Tamper with ciphertext
        val lastChar = encrypted.takeLast(1)
        val replacementChar = if (lastChar == "0") "1" else "0"
        val tampered = encrypted.dropLast(1) + replacementChar

        assertThrows(IllegalArgumentException::class.java) {
            SyncPairingSession.decryptPayload(
                message = tampered,
                pin = samplePin,
                myKeyPair = serverKeys,
                peerPublicKey = clientKeys.publicKey
            )
        }
    }

    @Test
    fun testConstantTimeEquals() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        val c = byteArrayOf(1, 2, 3, 4, 6)
        val d = byteArrayOf(1, 2, 3)

        assertTrue(SyncPairingSession.constantTimeEquals(a, b))
        assertFalse(SyncPairingSession.constantTimeEquals(a, c))
        assertFalse(SyncPairingSession.constantTimeEquals(a, d))
        assertTrue(SyncPairingSession.constantTimeEquals(byteArrayOf(), byteArrayOf()))
    }
}
