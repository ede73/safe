package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

class DecryptableCategoryEntry {
    var id: Long? = null

    // TODO: Make IVCipherText
    var encryptedName = IVCipherText.getEmpty()
    val plainName: String
        get() = decrypt(encryptedName)
    private val keyStore = KeyStoreHelperFactory.getKeyStoreHelper()
    private fun decrypt(value: IVCipherText): String {
        return String(keyStore.decryptByteArray(value))
    }

    var containedPasswordCount = 0
}