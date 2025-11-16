package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText

object KeyStoreHelperFactory {
    var getKeyStoreHelper: () -> KeyStoreHelper = { KeyStoreHelper() }

    var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        getKeyStoreHelper().decryptByteArray(encrypted)
    }

    var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        getKeyStoreHelper().encryptByteArray(plaintext)
    }
}
