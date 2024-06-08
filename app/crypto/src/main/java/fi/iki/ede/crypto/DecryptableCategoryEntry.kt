package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// TODO: Doesn't really belong to this project, does it?
class DecryptableCategoryEntry {
    var id: Long? = null
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = decrypt(encryptedName)

    // TODO: UGLY, could be single use on demand
    // some other interface would be better for testing, we don't really need FULL keystore
    // and (lack of android keystore) causes issues with @Preview
    private val keyStore = KeyStoreHelperFactory.getKeyStoreHelper()

    private fun decrypt(value: IVCipherText): String {
        return String(keyStore.decryptByteArray(value))
    }

    var containedPasswordCount = 0

    // Flow state is annoying since it requires NEW ENTITIES for changes to register
    fun copy(): DecryptableCategoryEntry = DecryptableCategoryEntry().apply {
        id = this@DecryptableCategoryEntry.id
        encryptedName = this@DecryptableCategoryEntry.encryptedName
        containedPasswordCount = this@DecryptableCategoryEntry.containedPasswordCount
    }
}