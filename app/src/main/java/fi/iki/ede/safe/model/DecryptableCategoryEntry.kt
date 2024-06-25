package fi.iki.ede.safe.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// TODO: Doesn't really belong to this project, does it?
class DecryptableCategoryEntry {
    var id: Long? = null
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = decrypt(encryptedName)

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableCategoryEntry = DecryptableCategoryEntry().apply {
        id = this@DecryptableCategoryEntry.id
        encryptedName = this@DecryptableCategoryEntry.encryptedName
        containedSiteEntryCount = this@DecryptableCategoryEntry.containedSiteEntryCount
    }

    private val decrypter = KeyStoreHelperFactory.getDecrypter()

    private fun decrypt(value: IVCipherText): String {
        return String(decrypter(value))
    }

    var containedSiteEntryCount = 0

}