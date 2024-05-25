package fi.iki.ede.crypto

typealias HexString = String

fun ByteArray.toHexString(): HexString = joinToString("") {
    java.lang.Byte.toUnsignedInt(it).toString(radix = 16).padStart(2, '0')
}

fun HexString.hexToByteArray(): ByteArray {
    require(this.isNotEmpty()) { "Cannot convert empty to hexadecimal" }
    require(this.matches(Regex("^[0-9a-fA-F]+"))) { "To convert hexadecimal strings, you MUST pass hexadecimal string, not '$this'" }
    require(this.length % 2 == 0) { "Hexadecimal must be exactly divisible by 2" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
