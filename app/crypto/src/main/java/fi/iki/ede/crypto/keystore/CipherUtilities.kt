package fi.iki.ede.crypto.keystore

import android.security.keystore.KeyProperties
import java.security.SecureRandom
import javax.crypto.Cipher

abstract class CipherUtilities {
    fun getAESCipher(): Cipher = Cipher.getInstance(AES_MODE)

    companion object {
        const val KEY_LENGTH_BITS = 256
        const val IV_LENGTH = KEY_LENGTH_BITS / 8 / 2
        private const val AES_MODE =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
        const val KEY_ITERATION_COUNT = 20000

        fun generateRandomBytes(bits: Int): ByteArray {
            require(bits % 8 == 0) { "Salt bits NEED to be divisible by 8" }
            val randomBytes = ByteArray(bits / 8)
            val sr = SecureRandom()
            sr.nextBytes(randomBytes)
            return randomBytes
        }
    }
}
