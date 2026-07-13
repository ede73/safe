package fi.iki.ede.crypto.support

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory

// Addressed PR12 comment: Restored EncryptExtensions.kt for encryption helper extensions
fun Password.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider): IVCipherText {
    val bytes = this.utf8password.toUtf8ByteArray()
    val cipherText = encrypter(bytes)
    bytes.fill(0)
    return cipherText
}

fun String.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider) =
    encrypter(this.trim().encodeToByteArray())

fun ByteArray.encrypt(encrypter: (ByteArray) -> IVCipherText = KeyStoreHelperFactory.getKeyStoreHelper().encrypterProvider) =
    encrypter(this)

fun CharArray.toUtf8ByteArray(): ByteArray {
    val byteList = mutableListOf<Byte>()
    var i = 0
    while (i < this.size) {
        val codePoint = this[i].code
        if (codePoint < 0x80) {
            byteList.add(codePoint.toByte())
        } else if (codePoint < 0x800) {
            byteList.add((0xc0 or (codePoint shr 6)).toByte())
            byteList.add((0x80 or (codePoint and 0x3f)).toByte())
        } else if (codePoint in 0xd800..0xdfff) {
            if (codePoint < 0xdc00 && i + 1 < this.size) {
                val next = this[i + 1].code
                if (next in 0xdc00..0xdfff) {
                    val surrogateCodePoint = (((codePoint and 0x3ff) shl 10) or (next and 0x3ff)) + 0x10000
                    byteList.add((0xf0 or (surrogateCodePoint shr 18)).toByte())
                    byteList.add((0x80 or ((surrogateCodePoint shr 12) and 0x3f)).toByte())
                    byteList.add((0x80 or ((surrogateCodePoint shr 6) and 0x3f)).toByte())
                    byteList.add((0x80 or (surrogateCodePoint and 0x3f)).toByte())
                    i++
                }
            }
        } else {
            byteList.add((0xe0 or (codePoint shr 12)).toByte())
            byteList.add((0x80 or ((codePoint shr 6) and 0x3f)).toByte())
            byteList.add((0x80 or (codePoint and 0x3f)).toByte())
        }
        i++
    }
    return byteList.toByteArray()
}
