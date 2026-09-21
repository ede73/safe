package fi.iki.ede.db.cxf

import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry

/**
 * Specialized synthetic SiteEntry wrapper for FIDO CXF credentials.
 * Inherits from [DecryptableSiteEntry] so all UI components can render it natively while holding
 * direct references to the underlying [CXFImport] or [CXFPasskey] entity.
 */
open class DecryptableCXFSiteEntry(
    categoryId: Long,
    val cxfImport: CXFImport? = null,
    val cxfPasskey: CXFPasskey? = null
) : DecryptableSiteEntry(categoryId = categoryId) {

    val isPasskey: Boolean get() = cxfPasskey != null

    init {
        id = if (cxfPasskey != null) {
            PASSKEY_SITE_ENTRY_ID_OFFSET - (cxfPasskey.id ?: 0L)
        } else {
            IMPORT_SITE_ENTRY_ID_OFFSET - (cxfImport?.id ?: 0L)
        }
    }

    override fun getPlainDescription(): String {
        val cached = _cachedPlainDescription
        if (cached != null) return cached
        val dec = description.decrypt()
        val candidate = if (dec.isNotBlank()) {
            dec
        } else if (cxfImport != null) {
            cxfImport.cachedDecryptedUrl
        } else if (cxfPasskey != null) {
            cxfPasskey.cachedDecryptedUrl.ifBlank { cxfPasskey.rpId }
        } else {
            dec
        }
        val cleaned = cleanDomainName(candidate).ifBlank { "Imported Credential" }
        _cachedPlainDescription = cleaned
        return cleaned
    }

    override fun getPlainWebsite(): String {
        val dec = website.decrypt()
        if (dec.isNotBlank()) return dec
        val desc = description.decrypt()
        if (desc.startsWith("http://", ignoreCase = true) ||
            desc.startsWith("https://", ignoreCase = true) ||
            desc.startsWith("www.", ignoreCase = true)
        ) {
            return desc
        }
        return dec
    }

    companion object {
        const val IMPORT_SITE_ENTRY_ID_OFFSET = -100000L
        const val PASSKEY_SITE_ENTRY_ID_OFFSET = -200000L

        fun isCxfSiteEntryId(id: Long?): Boolean =
            id != null && id <= IMPORT_SITE_ENTRY_ID_OFFSET

        fun cleanDomainName(rawNameOrUrl: String): String {
            val str = rawNameOrUrl.trim()
            if (str.isBlank()) return str
            val noScheme = if (str.contains("://")) {
                str.substringAfter("://")
            } else {
                str
            }
            val domain = noScheme.substringBefore("/").substringBefore("?").substringBefore("#").substringBefore(":")
            return domain.ifBlank { str }
        }

        fun makeFromImport(
            cxfAccountId: Long,
            cxfImport: CXFImport
        ): DecryptableCXFSiteEntry {
            val categorySyntheticId = DecryptableCXFCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            return DecryptableCXFSiteEntry(
                categoryId = categorySyntheticId,
                cxfImport = cxfImport
            ).apply {
                description = cxfImport.encryptedName
                username = cxfImport.encryptedUsername
                password = cxfImport.encryptedPassword
                website = cxfImport.encryptedUrl
                note = cxfImport.encryptedNote
            }
        }

        fun makeFromPasskey(
            cxfAccountId: Long,
            cxfPasskey: CXFPasskey
        ): DecryptableCXFSiteEntry {
            val categorySyntheticId = DecryptableCXFCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            return DecryptableCXFSiteEntry(
                categoryId = categorySyntheticId,
                cxfPasskey = cxfPasskey
            ).apply {
                description = cxfPasskey.encryptedName
                username = cxfPasskey.encryptedUsername
                password = fi.iki.ede.crypto.IVCipherText.getEmpty()
                website = cxfPasskey.encryptedUrl
                note = cxfPasskey.encryptedNote
            }
        }
    }
}
