package fi.iki.ede.safe.ui.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncIntegrationTest {
    @Test
    fun testDesktopSyncHandshake() = runBlocking {
        // Exercise the automatic-sync transport without depending on a real vault keystore.
        val pin = "a".repeat(64)
        val server = SyncNetworkService()
        var serverReceived: String? = null
        server.startServer(port = SAFE_SYNC_DEFAULT_PORT, pin = pin) { payloadJson ->
            serverReceived = payloadJson
            // echo back an empty payload
            Json.encodeToString(SyncPayload(listOf()))
        }
        // give server time to start
        delay(500)
        val clientPayload = Json.encodeToString(SyncPayload(listOf()))
        val responseJson = server.sendPayloadToHost(
            hostAddress = "127.0.0.1",
            port = server.boundPort,
            pin = pin,
            localPayloadJson = clientPayload
        )
        // simple assertions
        assertEquals(clientPayload, serverReceived, "Server did not receive expected payload")
        assertTrue(responseJson.isNotBlank(), "Client received empty response")
        server.stopServer()
    }
}
