package fi.iki.ede.crypto.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import fi.iki.ede.crypto.EncryptedPassword
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyManagement.decryptMasterKey
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyManagement.generateRandomBytes
import fi.iki.ede.crypto.keystore.KeyManagement.makeFreshNewKey
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object KeyStoreHelperFactory {
    fun getKeyStoreHelper() = KeyStoreHelper()
}

// TODO: Missing key rotation
// Some apps claim android keystore doesn't work with passwords but then
// some PBE support seems to exist according to https://developer.android.com/reference/java/security/KeyStore
// See AES issue
// https://github.com/realm/realm-java/issues/1306
/**
 * Two modes:
 * 1) NEW -> Fresh start, we're making new secret key based on user password
 * 2) IMPORT -> Something went wrong with android KeyStore like finger print register, factory reset,
 *    new phone, broken KeyStore implementation, accidental data clear, complete uninstall etc.
 *    In this case we will restore provided master key!
 */
class KeyStoreHelper {
    // https://developer.android.com/reference/java/security/KeyStore
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE)

    init {
        keyStore.load(null)
        check(keyStore.containsAlias(KEY_SECRET_MASTERKEY)) { "Keystore MUST have been initialized with importExistingKey or createNewKey" }
    }

    /**
     * This in current form is dangerous. Once the keystore has been deleted, there is NO GOING BACK
     * All data is lost for ever
     */
//    fun deleteKeyStore() {
//        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
//        keyStore.load(null)
//        keyStore.deleteEntry(KEY_SECRET_MASTERKEY)
//        keyStore.deleteEntry(KEY_BIOKEY)
//    }

    private fun getSecretKey(): Key {
        return keyStore.getKey(KEY_SECRET_MASTERKEY, null)!!
    }

    fun encryptByteArray(
        input: ByteArray,
        secretKey: Key = getSecretKey()
    ): IVCipherText {
        if (input.isEmpty()) {
            return IVCipherText(byteArrayOf(), byteArrayOf())
        }
        val c = getAESCipher()
        val iv = generateRandomBytes(c.blockSize * 8)
        c.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            IvParameterSpec(iv)
        )
        return IVCipherText(c.iv, c.doFinal(input))
    }

    fun decryptByteArray(
        encrypted: IVCipherText,
        secretKey: Key = getSecretKey()
    ): ByteArray {
        if (encrypted.iv.isEmpty()) {
            return byteArrayOf()
        }
        val c = getAESCipher()
        c.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            IvParameterSpec(encrypted.iv)
        )
        // TODO: Combine IV and cipher text?
        return c.doFinal(encrypted.cipherText)
    }

    private fun getAESCipher(): Cipher = Cipher.getInstance(AES_MODE)

    fun rotateKeys() {
        // I'm guessing rotation is re-initialization (unless there's a convenience method)
        // If reinit, then all keys need to be re-stored (we have one, so we can just
        // push it to the user to re-enroll)
        throw NotImplementedError("TODO: Keyrotation is mandatory")
    }

    fun getOrCreateBiokey(): SecretKey {
        keyStore.getKey(KEY_BIOKEY, null)?.let { return it as SecretKey }
        val paramsBuilder = KeyGenParameterSpec.Builder(
            KEY_BIOKEY,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE,
                    KeyProperties.ENCRYPTION_PADDING_PKCS7
                )
                // TODO: FIX
                .setRandomizedEncryptionRequired(false)
                // Invalidate key if user registers a new biometric
                // Kinda dumb, since the oldone might still be usable (perhaps added new finger)
                // but alas the API doesn't tell what changed, just that something changes, so...
                .setInvalidatedByBiometricEnrollment(true)
//                // this should force key expiration after X days
//                .setKeyValidityEnd(
//                    Date.from(
//                        LocalDate.now().plusDays(MAX_BIO_KEY_AGE_DAYS.toLong())
//                            .atStartOfDay(ZoneId.systemDefault())
//                            .toInstant()
//                    )
//                )
            setKeySize(256)
            //setUserAuthenticationRequired(true)
        }
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(paramsBuilder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SECRET_MASTERKEY = "secret_masterkey"
        private const val KEY_BIOKEY = "biometrics"
//        private const val MAX_BIO_KEY_AGE_DAYS = 31

        private const val AES_MODE =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
        const val KEY_LENGTH_BITS = 256
        const val IV_LENGTH = KEY_LENGTH_BITS / 8 / 2
        private const val KEY_ITERATION_COUNT = 20000

        // Called when user logs in
        // TODO: Refactor, no point re-importing same key over and over
        fun importExistingEncryptedMasterKey(
            saltedPassword: SaltedPassword,
            ivSecretKey: IVCipherText
        ) {
            val pbkdf2key = generatePBKDF2(saltedPassword.salt, saltedPassword.password)

            val unencryptedKey = decryptMasterKey(pbkdf2key, ivSecretKey)

            // decipher
            importANewMasterKey(unencryptedKey)
        }

        fun createNewKey(password: Password): Pair<Salt, IVCipherText> {
            val salt = Salt(generateRandomBytes(64))
            val pbkdf2key = generatePBKDF2(salt, password)

            val (unencryptedKey, cipheredKey) = makeFreshNewKey(KEY_LENGTH_BITS, pbkdf2key)
            importANewMasterKey(unencryptedKey)
            return Pair(salt, cipheredKey)
        }

        fun generatePBKDF2(
            salt: Salt,
            password: Password
        ) = generatePBKDF2AESKey(
            salt,
            KEY_ITERATION_COUNT,
            password,
            KEY_LENGTH_BITS
        )

        private fun importANewMasterKey(aesKey: SecretKeySpec) {
            val keyAlias = KEY_SECRET_MASTERKEY
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.setEntry(
                keyAlias,
                KeyStore.SecretKeyEntry(aesKey),
                getGenericKeyProt()
            )
        }

        private fun getGenericKeyProt(): KeyProtection {
            return KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE,
                    KeyProperties.ENCRYPTION_PADDING_PKCS7
                )
                .setRandomizedEncryptionRequired(false) // don't need randomized IV in keystore
                .build()
        }
    }
}

fun KeyStoreHelper.decryptByteArray(encryptedPassword: EncryptedPassword): Password {
    assert(!encryptedPassword.isEmpty())
    return Password(
        decryptByteArray(
            IVCipherText(
                KeyStoreHelper.IV_LENGTH,
                encryptedPassword.encryptedPassword
            )
        )
    )
}

fun KeyStoreHelper.encryptByteArray(plainPassword: Password): EncryptedPassword {
    assert(!plainPassword.isEmpty())
    return EncryptedPassword(encryptByteArray(plainPassword.password).combineIVAndCipherText())
}