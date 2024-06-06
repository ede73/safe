package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText

typealias HexString = String

// extension functions helping keep classes and code cleaner going between different representations
fun ByteArray.toHexString(): HexString = joinToString("") {
    java.lang.Byte.toUnsignedInt(it).toString(radix = 16).padStart(2, '0')
}

fun IVCipherText.toHexString(): HexString =
    "iv:${iv.toHexString()},cipherText:${cipherText.toHexString()}"

fun HexString.hexToByteArray(): ByteArray {
    require(this.isNotEmpty()) { "Cannot convert empty to hexadecimal" }
    require(this.matches(Regex("^[0-9a-fA-F]+"))) { "To convert hexadecimal strings, you MUST pass hexadecimal string, not '$this'" }
    require(this.length % 2 == 0) { "Hexadecimal must be exactly divisible by 2" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun EncryptedPassword.toHex() = ivCipherText.toHexString()
