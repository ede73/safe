@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.db

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.support.hexToByteArray
import fi.iki.ede.crypto.support.toHexString
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.crypto.support.decrypt
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

    private fun IVCipherText.serialize(): String {
        if (this.isEmpty()) return ""
        return Base64.getEncoder().encodeToString(this.iv) + ":" + Base64.getEncoder().encodeToString(this.cipherText)
    }

    private fun String.deserializeIVCipherText(): IVCipherText {
        if (this.isEmpty()) return IVCipherText.getEmpty()
        val parts = this.split(":")
        if (parts.size != 2) return IVCipherText.getEmpty()
        val iv = Base64.getDecoder().decode(parts[0])
        val cipherText = Base64.getDecoder().decode(parts[1])
        return IVCipherText(iv, cipherText)
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

    private fun parseSimpleJson(jsonText: String): Map<String, Map<String, List<String>>> {
        val result = mutableMapOf<String, MutableMap<String, List<String>>>()
        val cleaned = jsonText.trim()
        if (cleaned.isEmpty() || cleaned == "{}") return result

        val tableRegex = "\"([a-zA-Z0-9_-]+)\"\\s*:\\s*\\{([^\\}]*)\\}".toRegex()
        val matches = tableRegex.findAll(cleaned)
        for (match in matches) {
            val tableName = match.groupValues[1]
            val tableContent = match.groupValues[2]
            
            val rows = mutableMapOf<String, List<String>>()
            val rowRegex = "\"([0-9]+)\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val rowMatches = rowRegex.findAll(tableContent)
            for (rowMatch in rowMatches) {
                val rowId = rowMatch.groupValues[1]
                val colsContent = rowMatch.groupValues[2]
                val cols = colsContent.split(",").map { 
                    it.trim().removeSurrounding("\"") 
                }
                rows[rowId] = cols
            }
            result[tableName] = rows
        }
        return result
    }

    // PR 5 Comment addressed: Removed production mock database prepopulation checkPrepopulate() method

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
        val ivBytes = if (cols.size > 2) cols[2].hexToByteArray() else ByteArray(16)

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

    fun addCategory(entry: DecryptableCategoryEntry): DBID {
        val db = readDb().toMutableMap()
        val categoriesTable = db["categories"]?.toMutableMap() ?: mutableMapOf()
        val nextId = ((categoriesTable.keys.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L) + 1L)
        categoriesTable[nextId.toString()] = listOf(entry.encryptedName.serialize())
        db["categories"] = categoriesTable
        writeDb(db)
        return nextId
    }

    fun deleteCategory(id: DBID): Int {
        val db = readDb().toMutableMap()
        val categoriesTable = db["categories"]?.toMutableMap() ?: return 0
        if (categoriesTable.remove(id.toString()) != null) {
            db["categories"] = categoriesTable
            
            // Cascading delete site entries in this category
            val siteEntriesTable = db["site_entries"]?.toMutableMap() ?: mutableMapOf()
            val keysToRemove = siteEntriesTable.filter { it.value[0] == id.toString() }.keys
            keysToRemove.forEach { siteEntriesTable.remove(it) }
            db["site_entries"] = siteEntriesTable
            
            writeDb(db)
            return 1
        }
        return 0
    }

    fun fetchAllCategoryRows(categoriesFlow: MutableStateFlow<List<DecryptableCategoryEntry>>? = null): List<DecryptableCategoryEntry> {
        val db = readDb()
        val categoriesTable = db["categories"] ?: return emptyList()
        val siteEntriesTable = db["site_entries"] ?: emptyMap()
        
        val list = categoriesTable.map { (idStr, cols) ->
            DecryptableCategoryEntry().apply {
                id = idStr.toLongOrNull() ?: 0L
                encryptedName = cols[0].deserializeIVCipherText()
                containedSiteEntryCount = siteEntriesTable.values.count { it[0] == idStr }
            }
        }
        if (categoriesFlow != null) {
            categoriesFlow.value = list
        }
        return list
    }

    fun updateCategory(id: DBID, entry: DecryptableCategoryEntry): Long {
        val db = readDb().toMutableMap()
        val categoriesTable = db["categories"]?.toMutableMap() ?: return 0L
        categoriesTable[id.toString()] = listOf(entry.encryptedName.serialize())
        db["categories"] = categoriesTable
        writeDb(db)
        return id
    }

    fun fetchPhotoOnly(siteEntryID: DBID): IVCipherText? = null

    fun fetchAllRows(
        categoryId: DBID? = null,
        softDeletedOnly: Boolean = false,
        siteEntriesFlow: MutableStateFlow<List<DecryptableSiteEntry>>? = null
    ): List<DecryptableSiteEntry> {
        val db = readDb()
        val siteEntriesTable = db["site_entries"] ?: return emptyList()
        
        val list = siteEntriesTable.mapNotNull { (idStr, cols) ->
            val entryCatId = cols[0].toLongOrNull() ?: 0L
            if (categoryId != null && entryCatId != categoryId) return@mapNotNull null
            
            DecryptableSiteEntry(entryCatId).apply {
                id = idStr.toLongOrNull() ?: 0L
                description = cols[1].deserializeIVCipherText()
                username = cols[2].deserializeIVCipherText()
                password = cols[3].deserializeIVCipherText()
                website = cols[4].deserializeIVCipherText()
                note = cols[5].deserializeIVCipherText()
                deleted = cols[6].toLongOrNull() ?: 0L
                if (cols.size > 7) {
                    photo = cols[7].deserializeIVCipherText()
                }
                if (cols.size > 8) {
                    extensions = cols[8].deserializeIVCipherText()
                }
                if (cols.size > 9) {
                    val dateSec = cols[9]
                    if (dateSec.isNotEmpty()) {
                        passwordChangedDate = dateSec.toLongOrNull()?.let {
                            fi.iki.ede.dateutils.DateUtils.unixEpochSecondsToInstant(it)
                        }
                    }
                }
            }
        }
        if (siteEntriesFlow != null) {
            siteEntriesFlow.value = list
        }
        return list
    }

    fun updateSiteEntry(entry: DecryptableSiteEntry): DBID {
        val db = readDb().toMutableMap()
        val siteEntriesTable = db["site_entries"]?.toMutableMap() ?: return 0L
        val idStr = entry.id.toString()
        siteEntriesTable[idStr] = listOf(
            entry.categoryId.toString(),
            entry.description.serialize(),
            entry.username.serialize(),
            entry.password.serialize(),
            entry.website.serialize(),
            entry.note.serialize(),
            entry.deleted.toString(),
            entry.photo.serialize(),
            entry.extensions.serialize(),
            entry.passwordChangedDate?.epochSeconds?.toString() ?: ""
        )
        db["site_entries"] = siteEntriesTable
        writeDb(db)
        return entry.id ?: 0L
    }

    fun updateSiteEntryCategory(id: DBID, newCategoryId: DBID): Int {
        val db = readDb().toMutableMap()
        val siteEntriesTable = db["site_entries"]?.toMutableMap() ?: return 0
        val cols = siteEntriesTable[id.toString()] ?: return 0
        val newCols = cols.toMutableList()
        newCols[0] = newCategoryId.toString()
        siteEntriesTable[id.toString()] = newCols
        db["site_entries"] = siteEntriesTable
        writeDb(db)
        return 1
    }

    fun addSiteEntry(entry: DecryptableSiteEntry): Long {
        val db = readDb().toMutableMap()
        val siteEntriesTable = db["site_entries"]?.toMutableMap() ?: mutableMapOf()
        val nextId = ((siteEntriesTable.keys.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L) + 1L)
        siteEntriesTable[nextId.toString()] = listOf(
            entry.categoryId.toString(),
            entry.description.serialize(),
            entry.username.serialize(),
            entry.password.serialize(),
            entry.website.serialize(),
            entry.note.serialize(),
            entry.deleted.toString(),
            entry.photo.serialize(),
            entry.extensions.serialize(),
            entry.passwordChangedDate?.epochSeconds?.toString() ?: ""
        )
        db["site_entries"] = siteEntriesTable
        writeDb(db)
        return nextId
    }

    fun fetchPhotoFilename(siteEntryID: DBID): FileName? = null
    fun loadPhoto(photoName: FileName): IVCipherText? = null
    fun deletePhoto(photoName: FileName) {}
    fun savePhoto(photo: IVCipherText): FileName? = null
    fun restoreSoftDeletedSiteEntry(id: DBID): Int = 0
    fun markSiteEntryDeleted(id: DBID): Int = 0
    
    fun hardDeleteSiteEntry(id: DBID): Int {
        val db = readDb().toMutableMap()
        val siteEntriesTable = db["site_entries"]?.toMutableMap() ?: return 0
        if (siteEntriesTable.remove(id.toString()) != null) {
            db["site_entries"] = siteEntriesTable
            writeDb(db)
            return 1
        }
        return 0
    }

    fun clearAllData() {
        val db = readDb().toMutableMap()
        db["categories"] = emptyMap()
        db["site_entries"] = emptyMap()
        writeDb(db)
    }
}
