package fi.iki.ede.safe.cxf

import fi.iki.ede.gpm.model.SavedGPM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FidoCxfParserTest {

    @Test
    fun testParseCxfJsonArrayPayload() {
        val jsonPayload = """
            [
                {
                    "title": "Google",
                    "url": "https://accounts.google.com",
                    "username": "user@gmail.com",
                    "password": "secretPassword123",
                    "notes": "Primary account"
                },
                {
                    "name": "GitHub",
                    "origin": "https://github.com",
                    "user": "developer",
                    "secret": "ghp_12345"
                }
            ]
        """.trimIndent()

        val items = FidoCxfParser.parseCxfPayloadToIncomingGPMs(jsonPayload)
        assertEquals(2, items.size)

        assertEquals("Google", items[0].name)
        assertEquals("https://accounts.google.com", items[0].url)
        assertEquals("user@gmail.com", items[0].username)
        assertEquals("secretPassword123", items[0].password)
        assertEquals("Primary account", items[0].note)

        assertEquals("GitHub", items[1].name)
        assertEquals("https://github.com", items[1].url)
        assertEquals("developer", items[1].username)
        assertEquals("ghp_12345", items[1].password)
    }

    @Test
    fun testParseCxfWrappedObjectPayload() {
        val jsonPayload = """
            {
                "credentials": [
                    {
                        "title": "Bitwarden",
                        "origin": "https://vault.bitwarden.com",
                        "username": "bw_user",
                        "password": "bw_password"
                    }
                ]
            }
        """.trimIndent()

        val items = FidoCxfParser.parseCxfPayloadToIncomingGPMs(jsonPayload)
        assertEquals(1, items.size)
        assertEquals("Bitwarden", items[0].name)
        assertEquals("https://vault.bitwarden.com", items[0].url)
        assertEquals("bw_user", items[0].username)
        assertEquals("bw_password", items[0].password)
    }

    @Test
    fun testGmsFidoCxfPayload() {
        val jsonPayload = """
            {"version":{"major":1,"minor":0},"exporterRpId":"passwords.google.com","exporterDisplayName":"Google Password Manager","timestamp":1789355108,"accounts":[{"id":"s8TePQC5pJz9eGwoQXYURbtzNuDM6YmqUL57-2P6hPQ","username":"","email":"tavaraturha963@gmail.com","collections":[],"items":[{"id":"JyZN-NaRp1qZOOr0fZi0gJY_izE","creationAt":1789354584,"modifiedAt":1789354584,"title":"http://192.168.1.1/","favorite":false,"scope":{"urls":["http://192.168.1.1/"],"androidApps":[]},"credentials":[{"type":"basic-auth","username":{"fieldType":"string","value":"marjakan"},"password":{"fieldType":"concealed-string","value":"]\"9:5+C-#u}.A'j("}}]}]}]}
        """.trimIndent()

        val cxfItems = FidoCxfParser.parseCxfPayloadToIncomingCXFList(jsonPayload)
        assertEquals(1, cxfItems.size)
        assertEquals("JyZN-NaRp1qZOOr0fZi0gJY_izE", cxfItems[0].cxfItemId)
        assertEquals("s8TePQC5pJz9eGwoQXYURbtzNuDM6YmqUL57-2P6hPQ", cxfItems[0].cxfAccountId)
        assertEquals("tavaraturha963@gmail.com", cxfItems[0].cxfAccountEmail)
        assertEquals("basic-auth", cxfItems[0].credentialType)
        assertEquals("http://192.168.1.1/", cxfItems[0].name)
        assertEquals("http://192.168.1.1/", cxfItems[0].url)
        assertEquals("marjakan", cxfItems[0].username)
        assertEquals("]\"9:5+C-#u}.A'j(", cxfItems[0].password)

        val items = FidoCxfParser.parseCxfPayloadToIncomingGPMs(jsonPayload)
        assertEquals(1, items.size)
        assertEquals("http://192.168.1.1/", items[0].name)
        assertEquals("http://192.168.1.1/", items[0].url)
        assertEquals("marjakan", items[0].username)
        assertEquals("]\"9:5+C-#u}.A'j(", items[0].password)
    }

    @Test
    fun testNoteCredentialParsing() {
        val jsonPayload = """
            {
                "version": {"major": 1, "minor": 0},
                "exporterDisplayName": "Google Password Manager",
                "accounts": [
                    {
                        "id": "acc123",
                        "email": "user@example.com",
                        "items": [
                            {
                                "id": "item123",
                                "title": "https://www.acme.com/",
                                "scope": {"urls": ["https://www.acme.com/"]},
                                "credentials": [
                                    {
                                        "type": "basic-auth",
                                        "username": {"fieldType": "string", "value": "hihhuliturha"},
                                        "password": {"fieldType": "concealed-string", "value": "eioooikeesalasana"}
                                    },
                                    {
                                        "type": "note",
                                        "content": {"fieldType": "string", "value": "noottia TULEEEEE!"}
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val cxfItems = FidoCxfParser.parseCxfPayloadToIncomingCXFList(jsonPayload)
        assertEquals(1, cxfItems.size)
        
        val basicAuth = cxfItems[0]
        assertEquals("basic-auth", basicAuth.credentialType)
        assertEquals("hihhuliturha", basicAuth.username)
        assertEquals("eioooikeesalasana", basicAuth.password)
        assertTrue(basicAuth.note.contains("noottia TULEEEEE!"))
    }

    @Test
    fun testMultiAccountCxfPayload() {
        val jsonPayload = """
            {
                "version": {"major": 1, "minor": 0},
                "exporterDisplayName": "Google Password Manager",
                "accounts": [
                    {
                        "id": "acc_work_123",
                        "email": "work@company.com",
                        "items": [
                            {
                                "id": "item_work_1",
                                "title": "Internal Portal",
                                "scope": {"urls": ["https://portal.company.com"]},
                                "credentials": [
                                    {
                                        "type": "basic-auth",
                                        "username": {"fieldType": "string", "value": "work_user"},
                                        "password": {"fieldType": "concealed-string", "value": "WorkPass123!"}
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "id": "acc_personal_456",
                        "email": "personal@gmail.com",
                        "items": [
                            {
                                "id": "item_personal_2",
                                "title": "Personal Bank",
                                "scope": {"urls": ["https://bank.example.com"]},
                                "credentials": [
                                    {
                                        "type": "password",
                                        "username": {"fieldType": "string", "value": "personal_user"},
                                        "password": {"fieldType": "concealed-string", "value": "BankSecret99!"}
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val cxfItems = FidoCxfParser.parseCxfPayloadToIncomingCXFList(jsonPayload)
        assertEquals(2, cxfItems.size)

        // Work Account Credential
        assertEquals("item_work_1", cxfItems[0].cxfItemId)
        assertEquals("acc_work_123", cxfItems[0].cxfAccountId)
        assertEquals("work@company.com", cxfItems[0].cxfAccountEmail)
        assertEquals("Internal Portal", cxfItems[0].name)
        assertEquals("https://portal.company.com", cxfItems[0].url)
        assertEquals("work_user", cxfItems[0].username)
        assertEquals("WorkPass123!", cxfItems[0].password)
        assertTrue(cxfItems[0].note.contains("work@company.com"))

        // Personal Account Credential
        assertEquals("item_personal_2", cxfItems[1].cxfItemId)
        assertEquals("acc_personal_456", cxfItems[1].cxfAccountId)
        assertEquals("personal@gmail.com", cxfItems[1].cxfAccountEmail)
        assertEquals("Personal Bank", cxfItems[1].name)
        assertEquals("https://bank.example.com", cxfItems[1].url)
        assertEquals("personal_user", cxfItems[1].username)
        assertEquals("BankSecret99!", cxfItems[1].password)
        assertTrue(cxfItems[1].note.contains("personal@gmail.com"))
    }

    @Test
    fun testSerializeSavedGPMsToCxfPayload() {
        val incoming = fi.iki.ede.gpm.model.IncomingGPM.makeFromCSVImport(
            name = "Test Site",
            url = "https://example.com",
            username = "testuser",
            password = "password1",
            note = "some note"
        )
        val saved = listOf(SavedGPM(id = 1L, importing = incoming))

        val cxfJson = FidoCxfParser.serializeSavedGPMsToCxfPayload(saved)
        assertTrue(cxfJson.contains("Test Site"))
        assertTrue(cxfJson.contains("https://example.com"))
        assertTrue(cxfJson.contains("testuser"))
        assertTrue(cxfJson.contains("password1"))
    }

    @Test
    fun testItemLevelUsernamePasswordFallback() {
        val jsonPayload = """
            {
                "version": {"major": 1, "minor": 0},
                "exporterDisplayName": "Google Password Manager",
                "accounts": [
                    {
                        "id": "acc_fallback",
                        "email": "user@example.com",
                        "items": [
                            {
                                "id": "item_fallback",
                                "title": "Fallback Site",
                                "username": "itemLevelUser",
                                "password": "itemLevelPassword",
                                "credentials": [
                                    {
                                        "type": "basic-auth"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val cxfItems = FidoCxfParser.parseCxfPayloadToIncomingCXFList(jsonPayload)
        assertEquals(1, cxfItems.size)
        assertEquals("itemLevelUser", cxfItems[0].username)
        assertEquals("itemLevelPassword", cxfItems[0].password)
    }
}
