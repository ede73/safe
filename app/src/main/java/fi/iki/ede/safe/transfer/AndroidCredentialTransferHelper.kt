package fi.iki.ede.safe.transfer

import android.content.Context
import androidx.credentials.providerevents.ProviderEventsManager
import androidx.credentials.providerevents.transfer.CredentialTypes
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.cxf.FidoCxfParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

private const val TAG = "CredentialTransfer"

object AndroidCredentialTransferHelper {

    /**
     * Imports credentials directly from a FIDO CXF JSON payload into the database.
     * Can be used with real IPC response or simulated fake CXF payload for emulator testing.
     */
    @OptIn(ExperimentalTime::class)
    fun processAndStoreCxfPayload(
        cxfJsonPayload: String,
        scope: CoroutineScope,
        onMessage: (String) -> Unit = {},
        complete: (Boolean, Int) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                onMessage("Parsing incoming credentials...")
                val incomingCXFs = FidoCxfParser.parseCxfPayloadToIncomingCXFList(cxfJsonPayload).toSet()

                if (incomingCXFs.isEmpty()) {
                    onMessage("No valid credentials found in payload.")
                    withContext(Dispatchers.Main) {
                        complete(false, 0)
                    }
                    return@launch
                }

                onMessage("Saving ${incomingCXFs.size} credentials securely to relational CXF database...")
                val database = fi.iki.ede.db.DBHelperFactory.getDBHelper().database

                val cxfEntities = mutableListOf<fi.iki.ede.db.cxf.CXFImport>()
                val passkeyEntities = mutableListOf<fi.iki.ede.db.cxf.CXFPasskey>()

                for ((cxfAccountId, cxfItemsGroup) in incomingCXFs.groupBy { it.cxfAccountId }) {
                    val email = cxfItemsGroup.firstOrNull()?.cxfAccountEmail ?: ""
                    val existingAccount = database.cxfAccountDao().getByCxfAccountId(cxfAccountId)
                    val parentAccountId = if (existingAccount != null && existingAccount.id != null) {
                        existingAccount.id!!
                    } else {
                        val newAccount = fi.iki.ede.db.cxf.CXFAccount(
                            cxfAccountId = cxfAccountId,
                            email = email,
                            importedAt = System.currentTimeMillis()
                        )
                        database.cxfAccountDao().insert(newAccount)
                    }

                    for (cxf in cxfItemsGroup) {
                        if (cxf.credentialType == "public-key") {
                            val existingPasskey = database.cxfPasskeyDao().getByCxfItemId(cxf.cxfItemId)
                            if (existingPasskey != null) {
                                val existingModified = existingPasskey.modifiedAt ?: 0L
                                val incomingModified = cxf.modifiedAt ?: System.currentTimeMillis()
                                if (cxf.modifiedAt == null || existingPasskey.modifiedAt == null || incomingModified >= existingModified) {
                                    val updatedPasskey = fi.iki.ede.db.cxf.CXFPasskey(
                                        id = existingPasskey.id,
                                        accountId = parentAccountId,
                                        cxfItemId = cxf.cxfItemId,
                                        cxfAccountId = cxf.cxfAccountId,
                                        rpId = extractRpIdFromRawJson(cxf.rawCredentialJson, cxf.url),
                                        name = cxf.name,
                                        url = cxf.url,
                                        username = cxf.username,
                                        credentialId = extractCredentialIdFromRawJson(cxf.rawCredentialJson),
                                        userHandle = extractUserHandleFromRawJson(cxf.rawCredentialJson),
                                        rawCredentialJson = cxf.rawCredentialJson,
                                        note = cxf.note,
                                        createdAt = cxf.creationAt ?: existingPasskey.createdAt,
                                        modifiedAt = cxf.modifiedAt ?: incomingModified,
                                        importedAt = cxf.importedAt,
                                        flaggedIgnored = existingPasskey.flaggedIgnored,
                                        hash = cxf.hash
                                    )
                                    passkeyEntities.add(updatedPasskey)
                                }
                            } else {
                                val newPasskey = fi.iki.ede.db.cxf.CXFPasskey(
                                    accountId = parentAccountId,
                                    cxfItemId = cxf.cxfItemId,
                                    cxfAccountId = cxf.cxfAccountId,
                                    rpId = extractRpIdFromRawJson(cxf.rawCredentialJson, cxf.url),
                                    name = cxf.name,
                                    url = cxf.url,
                                    username = cxf.username,
                                    credentialId = extractCredentialIdFromRawJson(cxf.rawCredentialJson),
                                    userHandle = extractUserHandleFromRawJson(cxf.rawCredentialJson),
                                    rawCredentialJson = cxf.rawCredentialJson,
                                    note = cxf.note,
                                    createdAt = cxf.creationAt,
                                    modifiedAt = cxf.modifiedAt,
                                    importedAt = cxf.importedAt,
                                    flaggedIgnored = false,
                                    hash = cxf.hash
                                )
                                passkeyEntities.add(newPasskey)
                            }
                        } else {
                            val existingImport = database.cxfImportDao().getByCxfItemId(cxf.cxfItemId)
                            if (existingImport != null) {
                                val existingModified = existingImport.modifiedAt ?: 0L
                                val incomingModified = cxf.modifiedAt ?: System.currentTimeMillis()
                                if (cxf.modifiedAt == null || existingImport.modifiedAt == null || incomingModified >= existingModified) {
                                    val updatedImport = fi.iki.ede.db.cxf.CXFImport(
                                        id = existingImport.id,
                                        accountId = parentAccountId,
                                        cxfItemId = cxf.cxfItemId,
                                        cxfAccountId = cxf.cxfAccountId,
                                        type = cxf.credentialType,
                                        name = cxf.name,
                                        url = cxf.url,
                                        username = cxf.username,
                                        password = cxf.password,
                                        rawCredentialJson = cxf.rawCredentialJson,
                                        note = cxf.note,
                                        createdAt = cxf.creationAt ?: existingImport.createdAt,
                                        modifiedAt = cxf.modifiedAt ?: incomingModified,
                                        importedAt = cxf.importedAt,
                                        flaggedIgnored = existingImport.flaggedIgnored,
                                        hash = cxf.hash
                                    )
                                    cxfEntities.add(updatedImport)
                                }
                            } else {
                                val newImport = fi.iki.ede.db.cxf.CXFImport(
                                    accountId = parentAccountId,
                                    cxfItemId = cxf.cxfItemId,
                                    cxfAccountId = cxf.cxfAccountId,
                                    type = cxf.credentialType,
                                    name = cxf.name,
                                    url = cxf.url,
                                    username = cxf.username,
                                    password = cxf.password,
                                    rawCredentialJson = cxf.rawCredentialJson,
                                    note = cxf.note,
                                    createdAt = cxf.creationAt,
                                    modifiedAt = cxf.modifiedAt,
                                    importedAt = cxf.importedAt,
                                    flaggedIgnored = false,
                                    hash = cxf.hash
                                )
                                cxfEntities.add(newImport)
                            }
                        }
                    }
                }

                if (cxfEntities.isNotEmpty()) {
                    database.cxfImportDao().insertAll(cxfEntities)
                }
                if (passkeyEntities.isNotEmpty()) {
                    database.cxfPasskeyDao().insertAll(passkeyEntities)
                }

                // Also save to GPMDB for UI compatibility
                val incomingGPMs = incomingCXFs.map { cxf ->
                    IncomingGPM.makeFromCSVImport(
                        name = cxf.name,
                        url = cxf.url,
                        username = cxf.username,
                        password = cxf.password,
                        note = cxf.note
                    )
                }.toSet()
                GPMDB.addNewIncomingGPM(incomingGPMs)

                onMessage("Successfully imported ${incomingCXFs.size} credentials!")
                withContext(Dispatchers.Main) {
                    complete(true, incomingCXFs.size)
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Storing CXF credentials failed: ${e.message}", e)
                onMessage("Import failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    complete(false, 0)
                }
            }
        }
    }

    /**
     * Launches direct credential transfer from Google Password Manager via Jetpack Credentials Provider Events API.
     */
    @OptIn(ExperimentalTime::class)
    fun launchDirectGpmImport(
        context: Context,
        scope: CoroutineScope,
        onMessage: (String) -> Unit = {},
        complete: (Boolean, Int) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                onMessage("Connecting to Android Credential Transfer...")
                val providerEventsManager = ProviderEventsManager.create(context)

                val importRequest = ImportCredentialsRequest(
                    credentialTypes = setOf(
                        "password",
                        CredentialTypes.CREDENTIAL_TYPE_BASIC_AUTH,
                        CredentialTypes.CREDENTIAL_TYPE_PUBLIC_KEY,
                        CredentialTypes.CREDENTIAL_TYPE_GENERATED_PASSWORD,
                        CredentialTypes.CREDENTIAL_TYPE_NOTE,
                        CredentialTypes.CREDENTIAL_TYPE_CUSTOM_FIELDS,
                        CredentialTypes.CREDENTIAL_TYPE_TOTP
                    ),
                    knownExtensions = emptySet()
                )

                onMessage("Waiting for user authorization...")
                val providerResponse = providerEventsManager.importCredentials(context, importRequest)
                val cxfPayload = providerResponse.response.responseJson
                Logger.d(TAG, "Received CXF payload (${cxfPayload.length} bytes)")

                processAndStoreCxfPayload(cxfPayload, scope, onMessage, complete)
            } catch (e: Throwable) {
                val cause = e.cause ?: e
                Logger.e(TAG, "Direct credential import failed (${cause.javaClass.name}): ${cause.message}", cause)
                val detailMsg = "${cause.javaClass.simpleName}: ${cause.message ?: "No matching provider or Play Services unavailable"}"
                onMessage("Import failed: $detailMsg")
                withContext(Dispatchers.Main) {
                    complete(false, 0)
                }
            }
        }
    }

    /**
     * Helper for testing in emulator with a sample/fake FIDO CXF payload including a Passkey (public-key).
     */
    fun createSampleFakeCxfPayload(): String {
        return """
            {
                "version": { "major": 1, "minor": 0 },
                "exporterRpId": "passwords.google.com",
                "exporterDisplayName": "Google Password Manager",
                "timestamp": 1789356704,
                "accounts": [
                    {
                        "id": "s8TePQC5pJz9eGwoQXYURbtzNuDM6YmqUL57-2P6hPQ",
                        "email": "tavaraturha963@gmail.com",
                        "items": [
                            {
                                "id": "item_passkey_github_001",
                                "creationAt": 1789356667,
                                "modifiedAt": 1789356667,
                                "title": "https://github.com",
                                "scope": {
                                    "urls": ["https://github.com"],
                                    "androidApps": []
                                },
                                "credentials": [
                                    {
                                        "type": "public-key",
                                        "userHandle": {
                                            "fieldType": "string",
                                            "value": "dXNlcmlkX2dpdGh1Yl8xMjM0NQ"
                                        },
                                        "username": {
                                            "fieldType": "string",
                                            "value": "developer_alice"
                                        },
                                        "userDisplayName": {
                                            "fieldType": "string",
                                            "value": "Alice Developer"
                                        },
                                        "credentialId": {
                                            "fieldType": "string",
                                            "value": "KzNnOGxXNG9Vd29pY2hDdzE4aFF2dz09"
                                        },
                                        "rpId": {
                                            "fieldType": "string",
                                            "value": "github.com"
                                        },
                                        "keyPair": {
                                            "algorithm": -7,
                                            "privateKey": {
                                                "fieldType": "concealed-string",
                                                "value": "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg5X7zY..."
                                            },
                                            "publicKey": {
                                                "fieldType": "string",
                                                "value": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9Z8x..."
                                            }
                                        },
                                        "transports": ["internal", "hybrid"],
                                        "signCount": 12
                                    }
                                ]
                            },
                            {
                                "id": "item_password_acme_002",
                                "creationAt": 1789356667,
                                "modifiedAt": 1789356667,
                                "title": "https://www.acme.com/",
                                "scope": {
                                    "urls": ["https://www.acme.com/"],
                                    "androidApps": []
                                },
                                "credentials": [
                                    {
                                        "type": "basic-auth",
                                        "username": {
                                            "fieldType": "string",
                                            "value": "hihhuliturha"
                                        },
                                        "password": {
                                            "fieldType": "concealed-string",
                                            "value": "eioooikeesalasana"
                                        }
                                    },
                                    {
                                        "type": "note",
                                        "content": {
                                            "fieldType": "string",
                                            "value": "noottia TULEEEEE!"
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
    }

    private fun extractRpIdFromRawJson(rawJson: String, fallbackUrl: String): String {
        try {
            if (rawJson.isNotBlank() && rawJson.startsWith("{")) {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
                if (element is kotlinx.serialization.json.JsonObject) {
                    val rpIdElem = element["rpId"] ?: element["rp"]
                    if (rpIdElem != null) {
                        val valStr = when (rpIdElem) {
                            is kotlinx.serialization.json.JsonPrimitive -> rpIdElem.content
                            is kotlinx.serialization.json.JsonObject -> rpIdElem["value"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                            else -> ""
                        }
                        if (valStr.isNotBlank()) return valStr
                    }
                }
            }
        } catch (_: Throwable) {}
        return fallbackUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
    }

    private fun extractCredentialIdFromRawJson(rawJson: String): String {
        try {
            if (rawJson.isNotBlank() && rawJson.startsWith("{")) {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
                if (element is kotlinx.serialization.json.JsonObject) {
                    val credIdElem = element["credentialId"] ?: element["id"]
                    if (credIdElem != null) {
                        return when (credIdElem) {
                            is kotlinx.serialization.json.JsonPrimitive -> credIdElem.content
                            is kotlinx.serialization.json.JsonObject -> credIdElem["value"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                            else -> ""
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return ""
    }

    private fun extractUserHandleFromRawJson(rawJson: String): String {
        try {
            if (rawJson.isNotBlank() && rawJson.startsWith("{")) {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
                if (element is kotlinx.serialization.json.JsonObject) {
                    val userHandleElem = element["userHandle"] ?: element["userId"]
                    if (userHandleElem != null) {
                        return when (userHandleElem) {
                            is kotlinx.serialization.json.JsonPrimitive -> userHandleElem.content
                            is kotlinx.serialization.json.JsonObject -> userHandleElem["value"]?.let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else "" } ?: ""
                            else -> ""
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return ""
    }
}
