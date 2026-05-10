package fi.iki.ede.crypto.keystore

actual typealias KMPKey = java.security.Key

actual class KMPSecretKeySpec actual constructor(actual val values: ByteArray) : javax.crypto.spec.SecretKeySpec(values, "AES")

actual fun fillRandomBytes(array: ByteArray) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        java.security.SecureRandom().nextBytes(array)
    } else {
        korlibs.crypto.SecureRandom.nextBytes(array)
    }
}
