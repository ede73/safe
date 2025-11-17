package fi.iki.ede.crypto.keystore

import androidx.annotation.VisibleForTesting
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.bits
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.bytes
import korlibs.crypto.AES
import korlibs.crypto.PBKDF2
import korlibs.crypto.Padding
import korlibs.crypto.SHA256

object KeyManagement {

    // --- Key generation (KMP) ---
    // Generate AES key (bits = 128/192/256)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun generateAESKey(bits: Int): ByteArray {
        require(bits == 128 || bits == 192 || bits == 256) {
            "AES key must be 128/192/256 bits"
        }
        return CipherUtilities.generateRandomBytes(bits.bits)
    }

    /**
     * Generate a new user password based secret key given the salt
     * This ALWAYS results in the same key for same inputs
     *
     * OpenSSL counter-part:
     * openssl enc -aes-256-cbc -k $PASSWORD -P -md sha256 -S $HEX_SALT -iter $ITERATIONS -pbkdf2
     *
     * Returns SecretKeySpec to preserve existing API.
     */
    fun generatePBKDF2AESKey(
        salt: Salt,
        iterationCount: Int,
        password: Password,
        keyLength: Int
    ): KMPSecretKeySpec {
        // original code expected salt length 8 (64/8) — keep same check for compatibility
        require(salt.salt.size == 64 / 8) { "Wrong amount of salt, expecting 8 bytes, got ${salt.salt.size}" }
        require(keyLength == 256) { "Only 256 bit keys supported" }

        // PBKDF2-HMAC-SHA256 derive keyLength bits
        val hash = PBKDF2.pbkdf2(
            password.utf8password.concatToString().encodeToByteArray(),
            salt.salt,
            iterationCount,
            keyLength,
            SHA256.invoke()
        )

        // validate length as before
        require((hash.bytes.size).let { it == 16 || it == 24 || it == 32 }) {
            "AES only supports 16,24 or 32 byte keys, you had ${hash.bytes.size}"
        }

        return KMPSecretKeySpec(hash.bytes, "AES")
    }

    fun makeFreshNewKey(
        keyLength: Int,
        pbkdf2key: KMPSecretKeySpec
    ): Pair<KMPSecretKeySpec, IVCipherText> =
        // Now generate new AES KEY (truly random)
        generateAESKey(keyLength).let { trulySecretAESKey ->
            encryptMasterKey(pbkdf2key, trulySecretAESKey).let { (iv, ciphertext) ->
                Pair(KMPSecretKeySpec(trulySecretAESKey, "AES"), IVCipherText(iv, ciphertext))
            }
        }

    /**
     * Encrypt given AES key with PBKDF2 key
     *
     * Result is ALWAYS random due to random IV
     */
    fun encryptMasterKey(
        pbkdf2key: KMPSecretKeySpec,
        unencryptedSecretKey: ByteArray,
    ): IVCipherText {
        // generate IV: AES CBC uses block size 16 bytes
        val iv = CipherUtilities.generateRandomBytes(CipherUtilities.IV_LENGTH.bytes)
        // AES CBC PKCS7 encryption via korlibs
        val cipherText =
            AES.encryptAesCbc(unencryptedSecretKey, pbkdf2key.values, iv, Padding.PKCS7Padding)
        return IVCipherText(iv, cipherText)
    }

    fun decryptMasterKey(
        pbkdf2key: KMPSecretKeySpec,
        secretKey: IVCipherText,
    ): KMPSecretKeySpec {
        require(secretKey.iv.size == CipherUtilities.IV_LENGTH) { "IV must be exactly ${CipherUtilities.IV_LENGTH}, not ${secretKey.iv.size}" }
        return KMPSecretKeySpec(
            AES.decryptAesCbc(
                secretKey.cipherText,
                pbkdf2key.values,
                secretKey.iv,
                Padding.PKCS7Padding
            ), "AES"
        )
    }
}
