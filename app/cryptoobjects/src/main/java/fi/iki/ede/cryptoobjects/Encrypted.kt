package fi.iki.ede.cryptoobjects

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

fun IVCipherText.decrypt() = String(KeyStoreHelperFactory.decrypterProvider(this))
fun String.encrypt() = KeyStoreHelperFactory.encrypterProvider(this.toByteArray())

