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

        fun makeFromImport(
            cxfAccountId: Long,
            cxfImport: CXFImport
        ): DecryptableGPMSiteEntry {
            val categorySyntheticId = DecryptableGPMCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            return DecryptableGPMSiteEntry(
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
        ): DecryptableGPMSiteEntry {
            val categorySyntheticId = DecryptableGPMCategoryEntry.CATEGORY_ID_OFFSET - cxfAccountId
            val displayRpId = cxfPasskey.rpId.ifBlank { "Passkey" }
            return DecryptableGPMSiteEntry(
                categoryId = categorySyntheticId,
                cxfPasskey = cxfPasskey
            ).apply {
                description = cxfPasskey.encryptedName
                username = cxfPasskey.encryptedUsername
                password = "[Passkey: $displayRpId]".encrypt()
                website = cxfPasskey.encryptedUrl
                note = cxfPasskey.encryptedNote
            }
        }
    }
}
