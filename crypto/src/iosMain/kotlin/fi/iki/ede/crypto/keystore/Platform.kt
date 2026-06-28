package fi.iki.ede.crypto.keystore

actual interface KMPKey {
    actual fun getAlgorithm(): String
    actual fun getFormat(): String
    actual fun getEncoded(): ByteArray
}

actual class KMPSecretKeySpec actual constructor(actual val values: ByteArray) : KMPKey {
    override fun getAlgorithm(): String = "AES"
    override fun getFormat(): String = "RAW"
    override fun getEncoded(): ByteArray = values
}

actual fun fillRandomBytes(array: ByteArray) {
    korlibs.crypto.SecureRandom.nextBytes(array)
}

actual val isWindows: Boolean = false

