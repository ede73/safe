package fi.iki.ede.safe.transfer

import fi.iki.ede.safe.cxf.FidoCxfParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidCredentialTransferHelperTest {

    @Test
    fun testSampleFakeCxfPayloadParsing() {
        val fakePayload = AndroidCredentialTransferHelper.createSampleFakeCxfPayload()
        val cxfItems = FidoCxfParser.parseCxfPayloadToIncomingCXFList(fakePayload)

        assertEquals(2, cxfItems.size)

        val passkeyItem = cxfItems.find { it.name.contains("github.com") }
        assertTrue(passkeyItem != null)
        assertEquals("public-key", passkeyItem?.credentialType)
        assertEquals("https://github.com", passkeyItem?.url)
        assertEquals("developer_alice", passkeyItem?.username)

        val passwordItem = cxfItems.find { it.name.contains("acme.com") }
        assertTrue(passwordItem != null)
        assertEquals("basic-auth", passwordItem?.credentialType)
        assertEquals("hihhuliturha", passwordItem?.username)
        assertEquals("eioooikeesalasana", passwordItem?.password)
    }
}
