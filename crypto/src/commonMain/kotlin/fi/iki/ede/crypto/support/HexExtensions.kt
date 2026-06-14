package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText
import kotlin.text.HexFormat

typealias HexString = String

// extension functions helping keep classes and code cleaner going between different representations
@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHexString(): HexString = this.toHexString(HexFormat.Default)

fun IVCipherText.toHexString(): HexString =
    "iv:${iv.toHexString()},cipherText:${cipherText.toHexString()}"

@OptIn(ExperimentalStdlibApi::class)
fun HexString.hexToByteArray(): ByteArray = this.hexToByteArray(HexFormat.Default)

fun EncryptedPassword.toHex() = ivCipherText.toHexString()
