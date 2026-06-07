package fi.iki.ede.cryptoobjects

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.support.decrypt

class DecryptableCategoryEntry {
    var id: Long? = null
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = encryptedName.decrypt()

    fun copy(): DecryptableCategoryEntry = DecryptableCategoryEntry().apply {
        id = this@DecryptableCategoryEntry.id
        encryptedName = this@DecryptableCategoryEntry.encryptedName
        containedSiteEntryCount = this@DecryptableCategoryEntry.containedSiteEntryCount
    }

    var containedSiteEntryCount = 0
}
