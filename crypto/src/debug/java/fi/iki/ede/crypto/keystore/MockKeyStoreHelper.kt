package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

object MockKeyStoreHelper {
    fun init() {
        KeyStoreHelperFactory.encrypterProvider = { plaintext: ByteArray ->
            IVCipherText(plaintext, plaintext)  // just echo input for mock
        }
        KeyStoreHelperFactory.decrypterProvider = { encrypted: IVCipherText ->
            encrypted.cipherText  // just return ciphertext for mock
        }
    }
}