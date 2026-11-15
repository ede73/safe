package fi.iki.ede.safe.ui.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPairingSessionTest {

    @Test
    fun testPinGenerationAndFormatting() {
        val pin = SyncPairingSession.generatePairingCode()
        assertEquals(SyncPairingSession.PAIRING_CODE_LENGTH, pin.length)
        assertTrue(pin.all { it in "0123456789abcdef" })

        val formatted = SyncPairingSession.formatPinForDisplay(pin)
        assertTrue(formatted.contains(" - "))
        val normalized = SyncPairingSession.normalizePinInput(formatted)
        assertEquals(pin, normalized)
    }

    @Test
    fun testKeyAgreementAndDirectionalDerivation() {
        val clientPair = SyncPairingSession.generateEphemeralKeyPair()
        val serverPair = SyncPairingSession.generateEphemeralKeyPair()

        // Client derives with (clientPriv, clientPub, serverPub)
        val clientSession = SyncPairingSession.deriveSessionKeys(
            clientPair.privateKey,
            clientPair.publicKey,
            serverPair.publicKey
        )

        // Server derives with (serverPriv, serverPub, clientPub)
        val serverSession = SyncPairingSession.deriveSessionKeys(
            serverPair.privateKey,
            serverPair.publicKey,
            clientPair.publicKey
        )

        // Keys must match exactly
        assertTrue(SyncPairingSession.constantTimeEquals(clientSession.clientToServerAesKey, serverSession.clientToServerAesKey))
        assertTrue(SyncPairingSession.constantTimeEquals(clientSession.clientToServerMacKey, serverSession.clientToServerMacKey))
        assertTrue(SyncPairingSession.constantTimeEquals(clientSession.serverToClientAesKey, serverSession.serverToClientAesKey))
        assertTrue(SyncPairingSession.constantTimeEquals(clientSession.serverToClientMacKey, serverSession.serverToClientMacKey))
        assertTrue(SyncPairingSession.constantTimeEquals(clientSession.longTermClusterSecret, serverSession.longTermClusterSecret))
        assertEquals(clientSession.sasCode, serverSession.sasCode)

        // Directional keys must be distinct
        assertFalse(SyncPairingSession.constantTimeEquals(clientSession.clientToServerAesKey, clientSession.serverToClientAesKey))
        assertFalse(SyncPairingSession.constantTimeEquals(clientSession.clientToServerMacKey, clientSession.serverToClientMacKey))
    }

    @Test
    fun lowOrderPeerKeyIsRejected() {
        val local = SyncPairingSession.generateEphemeralKeyPair()
        assertFailsWith<IllegalArgumentException> {
            SyncPairingSession.deriveSessionKeys(local.privateKey, local.publicKey, ByteArray(32))
        }
    }

    @Test
    fun testMutualSasAuthentication() {
        val clientPair = SyncPairingSession.generateEphemeralKeyPair()
        val serverPair = SyncPairingSession.generateEphemeralKeyPair()
        val pin = "84920153"
        val wrongPin = "11223344"

        val clientSession = SyncPairingSession.deriveSessionKeys(
            clientPair.privateKey,
            clientPair.publicKey,
            serverPair.publicKey
        )
        val serverSession = SyncPairingSession.deriveSessionKeys(
            serverPair.privateKey,
            serverPair.publicKey,
            clientPair.publicKey
        )

        // 1. Client creates auth tag with its C2S MAC key and PIN
        val clientTag = SyncPairingSession.createAuthTag(clientSession.clientToServerMacKey, pin)

        // Server verifies client tag
        assertTrue(SyncPairingSession.verifyAuthTag(clientTag, serverSession.clientToServerMacKey, pin))
        // Server rejects wrong PIN
        assertFalse(SyncPairingSession.verifyAuthTag(clientTag, serverSession.clientToServerMacKey, wrongPin))
        // Server rejects tampered tag
        assertFalse(SyncPairingSession.verifyAuthTag(clientTag + "x", serverSession.clientToServerMacKey, pin))

        // 2. Server creates auth OK tag with its S2C MAC key and PIN
        val serverOkTag = SyncPairingSession.createAuthOkTag(serverSession.serverToClientMacKey, pin)

        // Client verifies server OK tag
        assertTrue(SyncPairingSession.verifyAuthOkTag(serverOkTag, clientSession.serverToClientMacKey, pin))
        // Client rejects wrong PIN
        assertFalse(SyncPairingSession.verifyAuthOkTag(serverOkTag, clientSession.serverToClientMacKey, wrongPin))
    }

    @Test
    fun testDirectionalPayloadEncryptionAndTamperResistance() {
        val clientPair = SyncPairingSession.generateEphemeralKeyPair()
        val serverPair = SyncPairingSession.generateEphemeralKeyPair()

        val clientSession = SyncPairingSession.deriveSessionKeys(
            clientPair.privateKey,
            clientPair.publicKey,
            serverPair.publicKey
        )
        val serverSession = SyncPairingSession.deriveSessionKeys(
            serverPair.privateKey,
            serverPair.publicKey,
            clientPair.publicKey
        )

        val plaintextC2S = "{\"vault\":\"secret_client_data_123\"}"
        val encryptedC2S = SyncPairingSession.encryptWithKeys(
            plaintextC2S,
            clientSession.clientToServerAesKey,
            clientSession.clientToServerMacKey
        )

        // Server decrypts C2S
        val decryptedC2S = SyncPairingSession.decryptWithKeys(
            encryptedC2S,
            serverSession.clientToServerAesKey,
            serverSession.clientToServerMacKey
        )
        assertEquals(plaintextC2S, decryptedC2S)

        // Server encrypts S2C response
        val plaintextS2C = "{\"vault\":\"secret_server_response_456\"}"
        val encryptedS2C = SyncPairingSession.encryptWithKeys(
            plaintextS2C,
            serverSession.serverToClientAesKey,
            serverSession.serverToClientMacKey
        )

        // Client decrypts S2C
        val decryptedS2C = SyncPairingSession.decryptWithKeys(
            encryptedS2C,
            clientSession.serverToClientAesKey,
            clientSession.serverToClientMacKey
        )
        assertEquals(plaintextS2C, decryptedS2C)

        // Tampering with payload fails authentication
        val tampered = encryptedC2S.replaceFirst("0", "1")
        assertFailsWith<IllegalArgumentException> {
            SyncPairingSession.decryptWithKeys(
                tampered,
                serverSession.clientToServerAesKey,
                serverSession.clientToServerMacKey
            )
        }
    }
}
