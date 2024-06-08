package fi.iki.ede.gpm.model

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

val encrypter = KeyStoreHelperFactory.getEncrypter()
val decrypter = KeyStoreHelperFactory.getDecrypter()

fun String.encrypt() = encrypter(this.toByteArray())
fun IVCipherText.decrypt() = String(decrypter(this))

