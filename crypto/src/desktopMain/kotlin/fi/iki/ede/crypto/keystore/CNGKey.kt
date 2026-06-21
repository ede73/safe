package fi.iki.ede.crypto.keystore

import java.security.Key

class CNGKey(val alias: String) : Key {
    override fun getAlgorithm(): String = "RSA"
    override fun getFormat(): String? = null
    override fun getEncoded(): ByteArray? = null
}
