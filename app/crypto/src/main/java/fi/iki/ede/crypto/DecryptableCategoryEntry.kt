package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

class DecryptableCategoryEntry {
    var id: Long? = null

    // TODO: Make IVCipherText
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = decrypt(encryptedName)

    // TODO: UGLY, could be single use on demand
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