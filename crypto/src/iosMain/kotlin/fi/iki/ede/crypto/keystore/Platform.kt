package fi.iki.ede.crypto.keystore

actual interface KMPKey

actual class KMPSecretKeySpec actual constructor(actual val values: ByteArray)

actual fun fillRandomBytes(array: ByteArray) {
    korlibs.crypto.SecureRandom.nextBytes(array)
}
