package fi.iki.ede.db.cxf

import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry

/**
 * Specialized synthetic Category entry wrapper for Google Password Manager / FIDO CXF accounts.
 * Inherits from [DecryptableCategoryEntry] so all UI components can render it natively while holding
 * a direct reference to the underlying [CXFAccount].
 */
open class DecryptableGPMCategoryEntry(
    val cxfAccount: CXFAccount
) : DecryptableCategoryEntry() {

    init {
        val accountId = cxfAccount.id ?: 0L
        val emailName = cxfAccount.cachedDecryptedEmail.ifBlank { cxfAccount.cxfAccountId }
        id = CATEGORY_ID_OFFSET - accountId
        encryptedName = "Google Pwd Manager - $emailName".encrypt()
    }

    companion object {
        const val CATEGORY_ID_OFFSET = -1000L

        fun isGpmCategoryId(id: Long?): Boolean =
            id != null && id <= CATEGORY_ID_OFFSET && id > (CATEGORY_ID_OFFSET - 100000L)
    }
}
