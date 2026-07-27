package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// Addressed PR12 comment: Restored DecryptExtensions.kt for decryption helper extensions
fun IVCipherText.decrypt(decrypter: (IVCipherText) -> ByteArray = KeyStoreHelperFactory.getKeyStoreHelper().decrypterProvider) =
    decrypter(this).decodeToString()
