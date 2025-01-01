package fi.iki.ede.crypto.keystore

import android.security.keystore.KeyProperties
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import org.jetbrains.annotations.TestOnly
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object KeyManagement {

    fun generateAESKey(bits: Int): ByteArray =
        KeyGenerator.getInstance("AES").let {
            it.init(bits)
            it.generateKey().encoded
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
        require(keyLength == 256) { "Only 256 bit keys supported" }

        val keyBytes =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
                PBEKeySpec(
                    password.utf8password,
                    salt.salt,
                    iterationCount,
                    keyLength
                )
            ).encoded

        // 16,24 or 32
        require(keyBytes.size.let { it == 16 || it == 24 || it == 32 }) { "AES only supports 16,24 or 32 byte keys" }
        OpenSSLExamples.debugPBKDFAESKey(password, salt, iterationCount, keyBytes)

        return SecretKeySpec(keyBytes, "AES")
    }

    fun makeFreshNewKey(
        keyLength: Int,
        pbkdf2key: SecretKeySpec
    ): Pair<SecretKeySpec, IVCipherText> =
        // Now generate new AES KEY
        generateAESKey(keyLength).let { trulySecretAESKey ->
            OpenSSLExamples.dumpAESKey(trulySecretAESKey)
            encryptMasterKey(pbkdf2key, trulySecretAESKey).let { (iv, ciphertext) ->
                OpenSSLExamples.makeNewKey(iv, ciphertext)
                Pair(SecretKeySpec(trulySecretAESKey, "AES"), IVCipherText(iv, ciphertext))
            }
        }

    /**
     * Encrypt given AES key with PBKDF2 key
     *
     * Result is ALWAYS totally random
     */
    fun encryptMasterKey(
        pbkdf2key: SecretKeySpec,
        unencryptedSecretKey: ByteArray,
    ): IVCipherText =
        getAESCipher().let { cipher ->
            cipher.init(
                Cipher.ENCRYPT_MODE,
                pbkdf2key,
                IvParameterSpec(CipherUtilities.generateRandomBytes(cipher.blockSize * 8))
            )
            IVCipherText(cipher.iv, cipher.doFinal(unencryptedSecretKey))
        }

    fun decryptMasterKey(
        pbkdf2key: SecretKeySpec,
        secretKey: IVCipherText,
    ): SecretKeySpec {
        require(secretKey.iv.size == CipherUtilities.IV_LENGTH) { "IV must be exactly keysize/16  ie. ${CipherUtilities.IV_LENGTH}, not ${secretKey.iv.size}" }
        getAESCipher().let { cipher ->
            cipher.init(Cipher.DECRYPT_MODE, pbkdf2key, IvParameterSpec(secretKey.iv))
            return SecretKeySpec(cipher.doFinal(secretKey.cipherText), "AES")
        }
    }

    private fun getAESCipher(): Cipher = try {
        Cipher.getInstance(AES_MODE)
    } catch (ex: Exception) {
        // NOt used in instrumentation tests at least, TODO: check unint tests use...
        Cipher.getInstance(AES_MODE_UNITTESTS)
    }


    private const val AES_MODE =
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"

    // Java cipher is PKCS#7 cipher, but incorrectly named PKCS5
    @get:TestOnly
    private val AES_MODE_UNITTESTS =
        AES_MODE.replace(KeyProperties.ENCRYPTION_PADDING_PKCS7, "PKCS5Padding")
}