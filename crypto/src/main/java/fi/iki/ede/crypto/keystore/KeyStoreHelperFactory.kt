package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText
import java.security.Key

object KeyStoreHelperFactory {
    lateinit var provideKeyStoreHelper: KeyStoreHelper
    var getKeyStoreHelper: () -> KeyStoreHelper = { provideKeyStoreHelper }

    var decrypterProviderWithKey: (IVCipherText, Key) -> ByteArray = { encrypted, key ->
        getKeyStoreHelper().decryptByteArray(encrypted, key)
    }

    var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        getKeyStoreHelper().decryptByteArray(encrypted)
    }

    var encrypterProviderWithKey: (ByteArray, Key) -> IVCipherText = { plaintext, key ->
        getKeyStoreHelper().encryptByteArray(plaintext, key)
    }
    var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        getKeyStoreHelper().encryptByteArray(plaintext)
    }
}
