package fi.iki.ede.db.cxf

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry

/**
 * Specialized synthetic SiteEntry wrapper for Google Password Manager / FIDO CXF credentials.
 * Inherits from [DecryptableSiteEntry] so all UI components can render it natively while holding
 * direct references to the underlying [CXFImport] or [CXFPasskey] entity.
 */
open class DecryptableGPMSiteEntry(
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

    companion object {
        const val IMPORT_SITE_ENTRY_ID_OFFSET = -100000L
        const val PASSKEY_SITE_ENTRY_ID_OFFSET = -200000L

        fun isGpmSiteEntryId(id: Long?): Boolean =
            id != null && id <= IMPORT_SITE_ENTRY_ID_OFFSET

        fun cleanDomainName(rawNameOrUrl: String): String {
            val str = rawNameOrUrl.trim()
            if (str.isBlank()) return str
            if (str.startsWith("http://", ignoreCase = true) ||
                str.startsWith("https://", ignoreCase = true) ||
                str.startsWith("www.", ignoreCase = true)
            ) {
                val noScheme = str.removePrefix("https://").removePrefix("HTTPS://")
                    .removePrefix("http://").removePrefix("HTTP://")
                val noWww = if (noScheme.startsWith("www.", ignoreCase = true)) noScheme.substring(4) else noScheme
                val domain = noWww.substringBefore("/")
                if (domain.isNotBlank()) {
                    return domain
                }
            }
            return str
        }

        fun makeFromImport(
            cxfAccountId: Long,
            cxfImport: CXFImport
        ): DecryptableGPMSiteEntry {
            val categorySyntheticId = DecryptableGPMCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            val rawName = cxfImport.cachedDecryptedName
            val rawUrl = cxfImport.cachedDecryptedUrl
            val nameCandidate = if (rawName.isNotBlank()) rawName else rawUrl
            val cleanName = cleanDomainName(nameCandidate).ifBlank { "Imported Credential" }
            val effectiveUrl = if (rawUrl.isNotBlank()) rawUrl else if (rawName.startsWith("http://", ignoreCase = true) || rawName.startsWith("https://", ignoreCase = true) || rawName.startsWith("www.", ignoreCase = true)) rawName else ""

            return DecryptableGPMSiteEntry(
                categoryId = categorySyntheticId,
                cxfImport = cxfImport
            ).apply {
                description = cleanName.encrypt()
                username = cxfImport.encryptedUsername
                password = cxfImport.encryptedPassword
                website = if (effectiveUrl.isNotBlank()) effectiveUrl.encrypt() else cxfImport.encryptedUrl
                note = cxfImport.encryptedNote
            }
        }

        fun makeFromPasskey(
            cxfAccountId: Long,
            cxfPasskey: CXFPasskey
        ): DecryptableGPMSiteEntry {
            val categorySyntheticId = DecryptableGPMCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            val displayRpId = cxfPasskey.rpId.ifBlank { "Passkey" }
            val rawName = cxfPasskey.cachedDecryptedName
            val rawUrl = cxfPasskey.cachedDecryptedUrl
            val nameCandidate = if (rawName.isNotBlank()) rawName else rawUrl
            val cleanName = if (nameCandidate.isNotBlank()) cleanDomainName(nameCandidate) else displayRpId
            val effectiveUrl = if (rawUrl.isNotBlank()) rawUrl else if (cxfPasskey.rpId.isNotBlank()) "https://${cxfPasskey.rpId}" else ""

            return DecryptableGPMSiteEntry(
                categoryId = categorySyntheticId,
                cxfPasskey = cxfPasskey
            ).apply {
                description = cleanName.encrypt()
                username = cxfPasskey.encryptedUsername
                password = "[Passkey: $displayRpId]".encrypt()
                website = if (effectiveUrl.isNotBlank()) effectiveUrl.encrypt() else cxfPasskey.encryptedUrl
                note = cxfPasskey.encryptedNote
            }
        }
    }
}
