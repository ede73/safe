package fi.iki.ede.safe.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// TODO: Doesn't really belong to this project, does it?
class DecryptableCategoryEntry {
    var id: Long? = null
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = decrypt(encryptedName)

    private val decrypter = KeyStoreHelperFactory.getDecrypter()

    private fun decrypt(value: IVCipherText): String {
        return String(decrypter(value))
    }

    var containedPasswordCount = 0

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableCategoryEntry = DecryptableCategoryEntry().apply {
        id = this@DecryptableCategoryEntry.id
        encryptedName = this@DecryptableCategoryEntry.encryptedName
        containedPasswordCount = this@DecryptableCategoryEntry.containedPasswordCount
    }
}