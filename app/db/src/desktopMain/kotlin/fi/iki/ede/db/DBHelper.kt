package fi.iki.ede.db

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import fi.iki.ede.crypto.keystore.KeyStoreHelper

typealias DBID = Long
typealias FileName = String

class DBHelper {
    private val dbFile = File("safe_db.json")

    init {
        // If file exists, load TEE/TPM keys into KeyStoreHelper static fields immediately so they are available when importing
        if (dbFile.exists()) {
            try {
                val tpmKeys = fetchTpmKeys()
                if (tpmKeys != null) {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val privateKeyBytes = Base64.getDecoder().decode(tpmKeys.first)
                    val publicKeyBytes = Base64.getDecoder().decode(tpmKeys.second)
                    
                    val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
                    
                    KeyStoreHelper.setLoadedKeys(privateKey, publicKey)
                }
            } catch (e: Exception) {
                // Ignore, helper will generate fallback keys if loading fails
            }
        }
    }

    private fun readDb(): Map<String, Map<String, List<String>>> {
        if (!dbFile.exists()) return emptyMap()
        val text = dbFile.readText()
        return parseSimpleJson(text)
    }

    private fun writeDb(db: Map<String, Map<String, List<String>>>) {
        val sb = StringBuilder()
        sb.append("{\n")
        val tableEntries = db.entries.toList()
        for (i in tableEntries.indices) {
            val tableEntry = tableEntries[i]
            sb.append("  \"${tableEntry.key}\": {\n")
            val rowEntries = tableEntry.value.entries.toList()
            for (j in rowEntries.indices) {
                val rowEntry = rowEntries[j]
                sb.append("    \"${rowEntry.key}\": [")
                val cols = rowEntry.value
                for (k in cols.indices) {
                    sb.append("\"${cols[k]}\"")
                    if (k < cols.size - 1) sb.append(", ")
                }
                sb.append("]")
                if (j < rowEntries.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("  }")
            if (i < tableEntries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        dbFile.writeText(sb.toString())
    }

    // A very simple JSON parser for the expected structure: { "table": { "id": ["col1", "col2"] } }
    private fun parseSimpleJson(jsonText: String): Map<String, Map<String, List<String>>> {
        val result = mutableMapOf<String, MutableMap<String, List<String>>>()
        val cleaned = jsonText.trim()
        if (cleaned.isEmpty() || cleaned == "{}") return result

        // Regex to extract tables: "tablename": { ... }
        val tableRegex = "\"([a-zA-Z0-9_-]+)\"\\s*:\\s*\\{([^\\}]*)\\}".toRegex()
        val matches = tableRegex.findAll(cleaned)
        for (match in matches) {
            val tableName = match.groupValues[1]
            val tableContent = match.groupValues[2]
            
            val rows = mutableMapOf<String, List<String>>()
            // Regex to extract rows: "rowid": [ ... ]
            val rowRegex = "\"([0-9]+)\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val rowMatches = rowRegex.findAll(tableContent)
            for (rowMatch in rowMatches) {
                val rowId = rowMatch.groupValues[1]
                val colsContent = rowMatch.groupValues[2]
                // Split columns by comma and trim quotes
                val cols = colsContent.split(",").map { 
                    it.trim().removeSurrounding("\"") 
                }
                rows[rowId] = cols
            }
            result[tableName] = rows
        }
        return result
    }

    fun storeSaltAndEncryptedMasterKey(salt: Salt, ivCipher: IVCipherText) {
        val db = readDb().toMutableMap()
        val keysTable = db["keys"]?.toMutableMap() ?: mutableMapOf()
        keysTable["1"] = listOf(salt.salt.toHexString(), ivCipher.cipherText.toHexString(), ivCipher.iv.toHexString())
        db["keys"] = keysTable
        writeDb(db)
    }

    fun fetchSaltAndEncryptedMasterKey(): Pair<Salt, IVCipherText> {
        val db = readDb()
        val keysTable = db["keys"] ?: throw Exception("No master key")
        val cols = keysTable["1"] ?: throw Exception("No master key")
        
        val saltBytes = cols[0].hexToByteArray()
        val cipherBytes = cols[1].hexToByteArray()
        val ivBytes = if (cols.size > 2) cols[2].hexToByteArray() else ByteArray(16) // Fallback to 16 bytes IV if old format

        return Pair(Salt(saltBytes), IVCipherText(ivBytes, cipherBytes))
    }

    fun storeTpmKeys(privateKeyBase64: String, publicKeyBase64: String) {
        val db = readDb().toMutableMap()
        val tpmTable = db["tpm_keys"]?.toMutableMap() ?: mutableMapOf()
        tpmTable["1"] = listOf(privateKeyBase64, publicKeyBase64)
        db["tpm_keys"] = tpmTable
        writeDb(db)
    }

    fun fetchTpmKeys(): Pair<String, String>? {
        val db = readDb()
        val tpmTable = db["tpm_keys"] ?: return null
        val cols = tpmTable["1"] ?: return null
        return Pair(cols[0], cols[1])
    }

    fun addCategory(entry: DecryptableCategoryEntry): DBID = 1L
    fun deleteCategory(id: DBID): Int = 0
    fun fetchAllCategoryRows(categoriesFlow: MutableStateFlow<List<DecryptableCategoryEntry>>? = null): List<DecryptableCategoryEntry> {
        val list = listOf(
            DecryptableCategoryEntry().apply {
                id = 1L
                encryptedName = "Social Media".encrypt()
                containedSiteEntryCount = 5
            },
            DecryptableCategoryEntry().apply {
                id = 2L
                encryptedName = "Email Accounts".encrypt()
                containedSiteEntryCount = 3
            },
            DecryptableCategoryEntry().apply {
                id = 3L
                encryptedName = "Banking & Finance".encrypt()
                containedSiteEntryCount = 2
            }
        )
        if (categoriesFlow != null) {
            categoriesFlow.value = list
        }
        return list
    }
    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry): Long = 0
    fun fetchPhotoOnly(siteEntryID: DBID): IVCipherText? = null
    fun fetchAllRows(
        categoryId: DBID? = null,
        softDeletedOnly: Boolean = false,
        siteEntriesFlow: MutableStateFlow<List<DecryptableSiteEntry>>? = null
    ): List<DecryptableSiteEntry> = emptyList()

    fun updateSiteEntry(entry: DecryptableSiteEntry): DBID = 0L
    fun updateSiteEntryCategory(id: DBID, newCategoryId: DBID): Int = 0
    fun addSiteEntry(entry: DecryptableSiteEntry): Long = 0
    fun fetchPhotoFilename(siteEntryID: DBID): FileName? = null
    fun loadPhoto(photoName: FileName): IVCipherText? = null
    fun deletePhoto(photoName: FileName) {}
    fun savePhoto(photo: IVCipherText): FileName? = null
    fun restoreSoftDeletedSiteEntry(id: DBID): Int = 0
    fun markSiteEntryDeleted(id: DBID): Int = 0
    fun hardDeleteSiteEntry(id: DBID): Int = 0
}
