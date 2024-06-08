package fi.iki.ede.crypto.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyManagement.decryptMasterKey
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyManagement.makeFreshNewKey
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// KeyStoreHelperFactory.provider = { MockKeyStoreHelper() }
object KeyStoreHelperFactory {
    var provider: () -> KeyStoreHelper = { KeyStoreHelper() }

    fun getKeyStoreHelper() = provider()
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
class KeyStoreHelper : CipherUtilities() {
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

    private fun getSecretKey() = keyStore.getKey(KEY_SECRET_MASTERKEY, null)!!

    fun encryptByteArray(
        input: ByteArray,
        secretKey: Key = getSecretKey()
    ) = if (input.isEmpty()) {
        IVCipherText(byteArrayOf(), byteArrayOf())
    } else
        getAESCipher().let {
            it.init(
                Cipher.ENCRYPT_MODE,
                secretKey,
                IvParameterSpec(generateRandomBytes(it.blockSize * 8))
            )
            IVCipherText(it.iv, it.doFinal(input))

        }

    fun decryptByteArray(
        encrypted: IVCipherText,
        secretKey: Key = getSecretKey()
    ): ByteArray = if (encrypted.iv.isEmpty()) {
        byteArrayOf()
    } else {
        getAESCipher().let {
            it.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                IvParameterSpec(encrypted.iv)
            )
            // TODO: Combine IV and cipher text?
            it.doFinal(encrypted.cipherText)
        }
    }

    fun rotateKeys() {
        // I'm guessing rotation is re-initialization (unless there's a convenience method)
        // If reinit, then all keys need to be re-stored (we have one, so we can just
        // push it to the user to re-enroll)
        throw NotImplementedError("TODO: Keyrotation is mandatory")
    }

    fun getOrCreateBiokey(): SecretKey =
        keyStore.getKey(KEY_BIOKEY, null)?.let { return it as SecretKey }
            ?: KeyGenParameterSpec.Builder(
                KEY_BIOKEY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).let {
                it.setBlockModes(KeyProperties.BLOCK_MODE_CBC)
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
                    .setKeySize(256)
                //setUserAuthenticationRequired(true)
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .let { keyGenerator ->
                        keyGenerator.init(it.build())
                        keyGenerator.generateKey()
                    }
            }


    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SECRET_MASTERKEY = "secret_masterkey"
        private const val KEY_BIOKEY = "biometrics"
//        private const val MAX_BIO_KEY_AGE_DAYS = 31

        // Called when user logs in
        // TODO: Refactor, no point re-importing same key over and over
        fun importExistingEncryptedMasterKey(
            saltedPassword: SaltedPassword,
            ivSecretKey: IVCipherText
        ): KeyStore = importANewMasterKey(
            decryptMasterKey(
                generatePBKDF2AESKey(
                    saltedPassword.salt,
                    KEY_ITERATION_COUNT,
                    saltedPassword.password,
                    KEY_LENGTH_BITS
                ), ivSecretKey
            )
        )

        fun createNewKey(password: Password): Pair<Salt, IVCipherText> =
            Salt(generateRandomBytes(64)).let { salt ->
                generatePBKDF2AESKey(
                    salt,
                    KEY_ITERATION_COUNT,
                    password,
                    KEY_LENGTH_BITS
                ).let { pbkdf2key ->
                    makeFreshNewKey(
                        KEY_LENGTH_BITS,
                        pbkdf2key
                    ).let { (unencryptedKey, cipheredKey) ->
                        importANewMasterKey(unencryptedKey)
                        Pair(salt, cipheredKey)
                    }
                }
            }

        private fun importANewMasterKey(aesKey: SecretKeySpec) =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                setEntry(
                    KEY_SECRET_MASTERKEY,
                    KeyStore.SecretKeyEntry(aesKey),
                    getGenericKeyProtection()
                )
            }

        private fun getGenericKeyProtection(): KeyProtection =
            KeyProtection.Builder(
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
