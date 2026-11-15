package fi.iki.ede.safe.sync

import fi.iki.ede.safe.ui.sync.SyncPairingSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncPairingSessionTest {

    @Test
    fun testPairingCodeGenerationAndFormatting() {
        val pin = SyncPairingSession.generatePairingCode()
        assertEquals(SyncPairingSession.PAIRING_CODE_LENGTH, pin.length)
        assertTrue(pin.all { it in "0123456789abcdef" })

        val formatted = SyncPairingSession.formatPinForDisplay(pin)
        assertEquals(39, formatted.length) // six groups of four characters
        assertTrue(formatted.contains(" - "))

        val normalized = SyncPairingSession.normalizePinInput(formatted)
        assertEquals(pin, normalized)
    }

    @Test
    fun testEcdhKeyExchangeAndMutualSasAuth() {
        val samplePin = "49208173"
        val wrongPin = "11112222"
        val originalPayload = """{"accounts":[{"email":"user@test.com","items":[{"title":"https://www.amazon.com/ap/signin"}]}]}"""

        // Client and Server generate ephemeral keypairs
        val clientKeys = SyncPairingSession.generateEphemeralKeyPair()
        val serverKeys = SyncPairingSession.generateEphemeralKeyPair()

        // 1. Hello exchange
        val clientHello = SyncPairingSession.createHelloMessage(clientKeys)
        val serverHello = SyncPairingSession.createHelloMessage(serverKeys)

        val clientPubReceivedByServer = SyncPairingSession.parseHelloMessage(clientHello)
        val serverPubReceivedByClient = SyncPairingSession.parseHelloMessage(serverHello)

        // 2. Derive session keys
        val clientSession = SyncPairingSession.deriveSessionKeys(
            clientKeys.privateKey,
            clientKeys.publicKey,
            serverPubReceivedByClient
        )
        val serverSession = SyncPairingSession.deriveSessionKeys(
            serverKeys.privateKey,
            serverKeys.publicKey,
            clientPubReceivedByServer
        )

        // 3. Mutual SAS verification
        val clientAuthTag = SyncPairingSession.createAuthTag(clientSession.clientToServerMacKey, samplePin)
        assertTrue(SyncPairingSession.verifyAuthTag(clientAuthTag, serverSession.clientToServerMacKey, samplePin))
        assertFalse(SyncPairingSession.verifyAuthTag(clientAuthTag, serverSession.clientToServerMacKey, wrongPin))

        val serverAuthOk = SyncPairingSession.createAuthOkTag(serverSession.serverToClientMacKey, samplePin)
        assertTrue(SyncPairingSession.verifyAuthOkTag(serverAuthOk, clientSession.serverToClientMacKey, samplePin))
        assertFalse(SyncPairingSession.verifyAuthOkTag(serverAuthOk, clientSession.serverToClientMacKey, wrongPin))

        // 4. Directional vault payload transfer
        val encryptedByClient = SyncPairingSession.encryptWithKeys(
            plainText = originalPayload,
            aesKey = clientSession.clientToServerAesKey,
            macKey = clientSession.clientToServerMacKey
        )
        assertNotEquals(originalPayload, encryptedByClient)

        val decryptedByServer = SyncPairingSession.decryptWithKeys(
            message = encryptedByClient,
            aesKey = serverSession.clientToServerAesKey,
            macKey = serverSession.clientToServerMacKey
        )
        assertEquals(originalPayload, decryptedByServer)

        // 5. Server response
        val serverResponse = """{"status":"converged","items":[]}"""
        val encryptedByServer = SyncPairingSession.encryptWithKeys(
            plainText = serverResponse,
            aesKey = serverSession.serverToClientAesKey,
            macKey = serverSession.serverToClientMacKey
        )
        val decryptedByClient = SyncPairingSession.decryptWithKeys(
            message = encryptedByServer,
            aesKey = clientSession.serverToClientAesKey,
            macKey = clientSession.serverToClientMacKey
        )
        assertEquals(serverResponse, decryptedByClient)
    }

    @Test
    fun testPayloadTamperingFailsHmacVerification() {
        val samplePin = "49208173"
        val originalPayload = """{"test":"tamper_proof_data"}"""

        val clientKeys = SyncPairingSession.generateEphemeralKeyPair()
        val serverKeys = SyncPairingSession.generateEphemeralKeyPair()

        val clientSession = SyncPairingSession.deriveSessionKeys(
            clientKeys.privateKey,
            clientKeys.publicKey,
            serverKeys.publicKey
        )
        val serverSession = SyncPairingSession.deriveSessionKeys(
            serverKeys.privateKey,
            serverKeys.publicKey,
            clientKeys.publicKey
        )

        val encrypted = SyncPairingSession.encryptWithKeys(
            plainText = originalPayload,
            aesKey = clientSession.clientToServerAesKey,
            macKey = clientSession.clientToServerMacKey
        )

        val tampered = encrypted.dropLast(1) + (if (encrypted.takeLast(1) == "0") "1" else "0")

        assertThrows(IllegalArgumentException::class.java) {
            SyncPairingSession.decryptWithKeys(
                message = tampered,
                aesKey = serverSession.clientToServerAesKey,
                macKey = serverSession.clientToServerMacKey
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
