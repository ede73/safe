package fi.iki.ede.crypto.keystore

import android.security.keystore.KeyProperties
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import org.jetbrains.annotations.TestOnly
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object KeyManagement {

    fun generateAESKey(bits: Int): ByteArray {
        val keygen = KeyGenerator.getInstance("AES")
        keygen.init(bits)
        return keygen.generateKey().encoded
    }


    fun generateRandomBytes(bits: Int): ByteArray {
        require(bits % 8 == 0) { "Salt bits NEED to be divisible by 8" }
        val randomBytes = ByteArray(bits / 8)
        val sr = SecureRandom()
        sr.nextBytes(randomBytes)
        return randomBytes
    }

    /**
     * Generate a new user password based secret key given the salt
     * This ALWAYS result in same key
     *
     * OpenSSL commandline counter part would be:
     * openssl enc -aes-256-cbc -k $PASSWORD -P -md sha256 -S $HEX_SALT -iter $ITERATIONS -pbkdf2
     *
     * It's KEY will match keyBytes.toHexString()
     */
    fun generatePBKDF2AESKey(
        salt: Salt,
        iterationCount: Int,
        password: Password,
        keyLength: Int
    ): SecretKeySpec {
        // TODO: Salt should actually be keyLength/8 (ie same size as the key) 32 for 256 AES
        require(salt.salt.size == 64 / 8) { "Wrong amount of salt, expecting 8 bytes, got ${salt.salt.size}" }

        val keySpec = PBEKeySpec(
            password.toCharArray(),
            salt.salt,
            iterationCount,
            keyLength
        )

        val keyBytes =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded

        OpenSSLExamples.debugPBKDFAESKey(password, salt, iterationCount, keyBytes)

        return SecretKeySpec(keyBytes, "AES")
    }

    fun makeFreshNewKey(
        keyLength: Int,
        pbkdf2key: SecretKeySpec
    ): Pair<SecretKeySpec, IVCipherText> {
        // Now generate new AES KEY
        val truelySecretAESKey = generateAESKey(keyLength)
        OpenSSLExamples.dumpAESKey(truelySecretAESKey)
        val (iv, ciphertext) = encryptMasterKey(pbkdf2key, truelySecretAESKey)
        OpenSSLExamples.makeNewKey(iv, ciphertext)
        return Pair(SecretKeySpec(truelySecretAESKey, "AES"), IVCipherText(iv, ciphertext))
    }

    /**
     * Encrypt given AES key with PBKDF2 key
     *
     * Result is ALWAYS totally random
     */
    fun encryptMasterKey(
        pbkdf2key: SecretKeySpec,
        unencryptedSecretKey: ByteArray,
    ): IVCipherText {
        val cipher = getAESCipher()
        val ivParams = IvParameterSpec(generateRandomBytes(cipher.blockSize * 8))
        cipher.init(Cipher.ENCRYPT_MODE, pbkdf2key, ivParams)
        val ciphertext = cipher.doFinal(unencryptedSecretKey)
        return IVCipherText(cipher.iv, ciphertext)
    }

    fun decryptMasterKey(
        pbkdf2key: SecretKeySpec,
        secretKey: IVCipherText,
    ): SecretKeySpec {
        require(secretKey.iv.size == KeyStoreHelper.IV_LENGTH) { "IV must be exactly keysize/16  ie. ${KeyStoreHelper.IV_LENGTH}, not ${secretKey.iv.size}" }
        val cipher = getAESCipher()
        cipher.init(Cipher.DECRYPT_MODE, pbkdf2key, IvParameterSpec(secretKey.iv))
        return SecretKeySpec(cipher.doFinal(secretKey.cipherText), "AES")
    }

    private fun getAESCipher(): Cipher {
        return try {
            Cipher.getInstance(AES_MODE)
        } catch (ex: Exception) {
            Cipher.getInstance(AES_MODE_UNITTESTS)
        }
    }

    private const val AES_MODE =
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"

    // Java cipher is PKCS#7 cipher, but incorrectly named PKCS5
    @get:TestOnly
    private val AES_MODE_UNITTESTS =
        AES_MODE.replace(KeyProperties.ENCRYPTION_PADDING_PKCS7, "PKCS5Padding")
}