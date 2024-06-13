package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.IVCipherText

fun IVCipherText.decrypt(decrypter: (IVCipherText) -> ByteArray) = String(decrypter(this))
