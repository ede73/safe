package fi.iki.ede.crypto.keystore

import java.security.Key
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

actual typealias KMPKey = Key

actual class KMPSecretKeySpec actual constructor(actual val values: ByteArray) : SecretKeySpec(values, "AES")

private val secureRandom = SecureRandom()

actual fun fillRandomBytes(array: ByteArray) {
    secureRandom.nextBytes(array)
}
