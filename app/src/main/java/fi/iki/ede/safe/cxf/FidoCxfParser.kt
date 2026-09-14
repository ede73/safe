package fi.iki.ede.safe.cxf

import fi.iki.ede.gpm.model.*
import kotlinx.serialization.json.*

object FidoCxfParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a FIDO CXF JSON payload into a list of IncomingCXF objects with item IDs, account IDs, and field types.
     */
    fun parseCxfPayloadToIncomingCXFList(cxfJsonPayload: String): List<IncomingCXF> {
        val result = mutableListOf<IncomingCXF>()
        if (cxfJsonPayload.isBlank()) return result

        try {
            val rootElement = json.parseToJsonElement(cxfJsonPayload)
            
            // 1. Try structured accounts -> items -> credentials
            if (rootElement is JsonObject && rootElement.containsKey("accounts")) {
                parseStructuredCxfToIncomingCXF(rootElement, result)
            }

            // 2. Fallback to flat/wrapped array or recursive discovery if result is empty
            if (result.isEmpty()) {
                parseFallbackCxfToIncomingCXF(rootElement, result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }

    private fun parseStructuredCxfToIncomingCXF(rootElement: JsonObject, result: MutableList<IncomingCXF>) {
        val exporterName = rootElement.extractStringValue("exporterDisplayName")
        val accounts = rootElement["accounts"] as? JsonArray ?: return

        for (account in accounts) {
            if (account !is JsonObject) continue
            val accountId = account.extractStringValue("id")
            val accountEmail = account.extractStringValue("email")
            val items = account["items"] as? JsonArray ?: continue

            val accountHeader = listOfNotNull(
                if (exporterName.isNotBlank()) exporterName else "Google Password Manager",
                if (accountEmail.isNotBlank()) accountEmail else null,
                if (accountId.isNotBlank()) "ID: $accountId" else null
            ).joinToString(" - ")

            for (item in items) {
                if (item !is JsonObject) continue

                val itemId = item.extractStringValue("id")
                val creationAt = (item["creationAt"] as? JsonPrimitive)?.longOrNull
                val modifiedAt = (item["modifiedAt"] as? JsonPrimitive)?.longOrNull
                val title = item.extractStringValue("title", "name", "label", "displayName")
                val url = item.extractUrlFromItem()
                val notes = item.extractStringValue("notes", "note", "comment")

                val credentials = item["credentials"] as? JsonArray
                val extraNotes = mutableListOf<String>()
                val mainCreds = mutableListOf<JsonObject>()
                val noteCreds = mutableListOf<JsonObject>()

                if (credentials != null) {
                    for (cred in credentials) {
                        if (cred !is JsonObject) continue
                        val credType = cred.extractStringValue("type").ifBlank { "basic-auth" }
                        val credNote = cred.extractNoteFromCred()
                        if (credNote.isNotBlank()) {
                            extraNotes.add(credNote)
                        }
                        if (credType == "note") {
                            noteCreds.add(cred)
                        } else {
                            mainCreds.add(cred)
                        }
                    }
                }

                val combinedNote = listOfNotNull(
                    if (accountHeader.isNotBlank()) accountHeader else null,
                    if (notes.isNotBlank()) notes else null,
                    if (extraNotes.isNotEmpty()) extraNotes.distinct().joinToString("\n") else null
                ).joinToString("\n")

                if (mainCreds.isNotEmpty()) {
                    for (cred in mainCreds) {
                        val credType = cred.extractStringValue("type").ifBlank { "basic-auth" }
                        val username = cred.extractUsernameFromCred().ifBlank { item.extractUsernameFromCred() }
                        val password = cred.extractPasswordFromCred().ifBlank { item.extractPasswordFromCred() }

                        val incomingCxf = IncomingCXF.make(
                            cxfItemId = itemId,
                            cxfAccountId = accountId,
                            cxfAccountEmail = accountEmail,
                            credentialType = credType,
                            name = if (title.isNotBlank()) title else if (url.isNotBlank()) url else "Imported Credential",
                            url = url,
                            username = username,
                            password = password,
                            rawCredentialJson = cred.toString(),
                            note = combinedNote,
                            creationAt = creationAt,
                            modifiedAt = modifiedAt
                        )
                        if (!result.contains(incomingCxf)) {
                            result.add(incomingCxf)
                        }
                    }
                } else if (noteCreds.isNotEmpty()) {
                    val firstNoteCred = noteCreds.first()
                    val incomingCxf = IncomingCXF.make(
                        cxfItemId = itemId,
                        cxfAccountId = accountId,
                        cxfAccountEmail = accountEmail,
                        credentialType = "note",
                        name = if (title.isNotBlank()) title else if (url.isNotBlank()) url else "Imported Note",
                        url = url,
                        username = "",
                        password = "",
                        rawCredentialJson = firstNoteCred.toString(),
                        note = combinedNote,
                        creationAt = creationAt,
                        modifiedAt = modifiedAt
                    )
                    if (!result.contains(incomingCxf)) {
                        result.add(incomingCxf)
                    }
                } else {
                    val username = item.extractUsernameFromCred()
                    val password = item.extractPasswordFromCred()
                    val itemNote = item.extractNoteFromCred()
                    if (username.isNotBlank() || password.isNotBlank() || itemNote.isNotBlank()) {
                        val incomingCxf = IncomingCXF.make(
                            cxfItemId = itemId,
                            cxfAccountId = accountId,
                            cxfAccountEmail = accountEmail,
                            credentialType = "password",
                            name = if (title.isNotBlank()) title else if (url.isNotBlank()) url else "Imported Credential",
                            url = url,
                            username = username,
                            password = password,
                            rawCredentialJson = item.toString(),
                            note = combinedNote,
                            creationAt = creationAt,
                            modifiedAt = modifiedAt
                        )
                        if (!result.contains(incomingCxf)) {
                            result.add(incomingCxf)
                        }
                    }
                }
            }
        }
    }

    private fun parseFallbackCxfToIncomingCXF(element: JsonElement, result: MutableList<IncomingCXF>) {
        val candidateObjects = mutableListOf<JsonObject>()
        findJsonObjects(element, candidateObjects)

        for (item in candidateObjects) {
            val name = item.extractStringValue("name", "title", "label", "displayName", "service")
            val url = item.extractUrlFromItem()
            val username = item.extractUsernameFromCred()
            val password = item.extractPasswordFromCred()
            val note = item.extractStringValue("note", "notes", "comment", "description")
            val itemId = item.extractStringValue("id")

            if (username.isNotBlank() || password.isNotBlank() || name.isNotBlank() || url.isNotBlank()) {
                val incomingCxf = IncomingCXF.make(
                    cxfItemId = itemId,
                    cxfAccountId = "",
                    cxfAccountEmail = "",
                    credentialType = "password",
                    name = if (name.isNotBlank()) name else if (url.isNotBlank()) url else "Imported Credential",
                    url = url,
                    username = username,
                    password = password,
                    note = note
                )
                if (!result.contains(incomingCxf)) {
                    result.add(incomingCxf)
                }
            }
        }
    }

    private fun JsonObject.extractUsernameFromCred(): String {
        return extractStringValue("username", "user", "account", "login", "userName", "email")
    }

    private fun JsonObject.extractPasswordFromCred(): String {
        val pwdElem = this["password"] ?: this["secret"] ?: this["pass"] ?: this["value"] ?: return ""
        if (pwdElem is JsonPrimitive) return pwdElem.content
        if (pwdElem is JsonObject) {
            val fieldType = pwdElem["fieldType"]?.extractRawString()
            if (fieldType == null || fieldType == "concealed-string" || fieldType == "string" || fieldType == "password") {
                return pwdElem["value"]?.extractRawString() ?: ""
            }
        }
        return ""
    }

    private fun JsonObject.extractNoteFromCred(): String {
        val noteElem = this["content"] ?: this["note"] ?: this["notes"] ?: this["comment"] ?: this["text"] ?: return ""
        if (noteElem is JsonPrimitive) return noteElem.content
        if (noteElem is JsonObject) {
            val fieldType = noteElem["fieldType"]?.extractRawString()
            if (fieldType == null || fieldType == "string" || fieldType == "text" || fieldType == "note") {
                return noteElem["value"]?.extractRawString() ?: noteElem.extractStringValue("value", "text", "content")
            }
        }
        return ""
    }

    /**
     * Parses a FIDO CXF JSON payload into a list of IncomingGPM objects for backward compatibility.
     */
    fun parseCxfPayloadToIncomingGPMs(cxfJsonPayload: String): List<IncomingGPM> {
        return parseCxfPayloadToIncomingCXFList(cxfJsonPayload).map { cxf ->
            IncomingGPM.makeFromCSVImport(
                name = cxf.name,
                url = cxf.url,
                username = cxf.username,
                password = cxf.password,
                note = cxf.note
            )
        }
    }

    private fun JsonObject.extractUrlFromItem(): String {
        // Direct url/origin field
        val directUrl = extractStringValue("url", "origin", "rpId", "site", "domain", "host", "uri")
        if (directUrl.isNotBlank()) return directUrl

        // FIDO CXF scope object: { "urls": ["http://..."], "androidApps": [...] }
        val scopeObj = this["scope"] as? JsonObject
        if (scopeObj != null) {
            val urlsArr = scopeObj["urls"] as? JsonArray
            if (urlsArr != null && urlsArr.isNotEmpty()) {
                val firstUrl = urlsArr[0].extractRawString()
                if (firstUrl.isNotBlank()) return firstUrl
            }
        }
        return ""
    }

    private fun findJsonObjects(element: JsonElement, accumulator: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("password") || element.containsKey("username") || element.containsKey("user") || element.containsKey("secret")) {
                    accumulator.add(element)
                } else {
                    for (value in element.values) {
                        findJsonObjects(value, accumulator)
                    }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findJsonObjects(item, accumulator)
                }
            }
            else -> {}
        }
    }

    private fun JsonElement.extractRawString(): String {
        return when (this) {
            is JsonPrimitive -> if (this.isString) this.content else this.contentOrNull ?: ""
            is JsonObject -> this.extractStringValue("value", "text", "content", "name", "password")
            else -> ""
        }
    }

    private fun JsonObject.extractStringValue(vararg keys: String): String {
        for (key in keys) {
            val elem = this[key] ?: continue
            val valStr = elem.extractRawString()
            if (valStr.isNotBlank()) return valStr
        }
        return ""
    }

    /**
     * Serializes a list of SavedGPM objects into a FIDO CXF JSON payload for exporting.
     */
    fun serializeSavedGPMsToCxfPayload(savedGPMs: List<SavedGPM>): String {
        val array = buildJsonArray {
            for (gpm in savedGPMs) {
                add(buildJsonObject {
                    put("type", "password")
                    put("title", gpm.cachedDecryptedName)
                    put("origin", gpm.cachedDecryptedUrl)
                    put("username", gpm.cachedDecryptedUsername)
                    put("password", gpm.cachedDecryptedPassword)
                    put("notes", gpm.cachedDecryptedNote)
                })
            }
        }
        return buildJsonObject {
            put("credentials", array)
        }.toString()
    }
}

