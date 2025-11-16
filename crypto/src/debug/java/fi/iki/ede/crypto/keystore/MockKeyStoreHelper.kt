package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

object MockKeyStoreHelper {
    fun init() {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    }
}
