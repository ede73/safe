package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText

typealias HexString = String

@OptIn(ExperimentalStdlibApi::class)
fun IVCipherText.toHexString(): HexString =
    "iv:${iv.toHexString()},cipherText:${cipherText.toHexString()}"

fun EncryptedPassword.toHex() = ivCipherText.toHexString()
