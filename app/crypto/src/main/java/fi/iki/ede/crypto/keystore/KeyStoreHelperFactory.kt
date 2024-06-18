package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

// KeyStoreHelperFactory.provider = { MockKeyStoreHelper() }
object KeyStoreHelperFactory {
    var provider: () -> KeyStoreHelper = { KeyStoreHelper() }
    fun getKeyStoreHelper() = provider()

    var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        getKeyStoreHelper().decryptByteArray(encrypted)
    }

    fun getDecrypter() = decrypterProvider

    var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        getKeyStoreHelper().encryptByteArray(plaintext)
    }

    fun getEncrypter() = encrypterProvider
}
