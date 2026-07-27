package fi.iki.ede.crypto.keystore

expect interface KMPKey {
    fun getAlgorithm(): String
    fun getFormat(): String
    fun getEncoded(): ByteArray
}

expect class KMPSecretKeySpec(values: ByteArray) {
    val values: ByteArray
}

expect fun fillRandomBytes(array: ByteArray)

expect val isWindows: Boolean

