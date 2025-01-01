package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

val decrypter = KeyStoreHelperFactory.getDecrypter()
val encrypter = KeyStoreHelperFactory.getEncrypter()

fun IVCipherText.decrypt() = String(decrypter(this))
fun String.encrypt() = encrypter(this.toByteArray())

