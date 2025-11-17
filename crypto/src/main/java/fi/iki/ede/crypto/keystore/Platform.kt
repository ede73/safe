package fi.iki.ede.crypto.keystore

import java.security.Key
import javax.crypto.spec.SecretKeySpec

interface KMPKey : Key
class KMPSecretKeySpec(val values: ByteArray, algorithm: String = "AES") :
    SecretKeySpec(values, algorithm)

// Minimal replacement for javax.crypto.spec.SecretKeySpec to keep API shape.
//class SecretKeySpec(val values: ByteArray, val algorithm: String = "AES")
