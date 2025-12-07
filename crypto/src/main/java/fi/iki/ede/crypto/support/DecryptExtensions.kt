package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

fun IVCipherText.decrypt(decrypter: (IVCipherText) -> ByteArray = KeyStoreHelperFactory.decrypterProvider) =
    String(decrypter(this))
