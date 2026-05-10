package fi.iki.ede.crypto.keystore

expect interface KMPKey

expect class KMPSecretKeySpec(values: ByteArray) {
    val values: ByteArray
}

expect fun fillRandomBytes(array: ByteArray)
