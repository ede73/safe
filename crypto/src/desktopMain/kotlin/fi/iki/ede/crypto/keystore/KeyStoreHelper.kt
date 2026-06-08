package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement.decryptMasterKey
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyManagement.makeFreshNewKey
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyStoreHelper(
    private val masterKey: KMPSecretKeySpec,
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey
) : IKeyStoreHelper {

    override fun testingDeleteKeys_DO_NOT_USE() {
        // Mock method for tests
    }

    override fun rotateKeys() {
        throw NotImplementedError("Key rotation is not supported on desktop")
    }

    override fun getOrCreateBiokey(): KMPKey {
        // Return private key as biokey representation
        return privateKey
    }

    override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray = { encrypted, key ->
        if (encrypted.iv.isEmpty()) {
            byteArrayOf()
        } else if (key is SecretKeySpec) {
            korlibs.crypto.AES.decryptAesCbc(encrypted.cipherText, key.encoded, encrypted.iv, korlibs.crypto.Padding.PKCS7Padding)
        } else if (key is PrivateKey) {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            cipher.doFinal(encrypted.cipherText)
        } else {
            throw IllegalArgumentException("Unsupported key type for decryption on desktop")
        }
    }

    override var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        if (encrypted.iv.isEmpty()) {
            byteArrayOf()
        } else {
            korlibs.crypto.AES.decryptAesCbc(encrypted.cipherText, masterKey.values, encrypted.iv, korlibs.crypto.Padding.PKCS7Padding)
        }
    }

    override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { plaintext, key ->
        if (key is SecretKeySpec) {
            val iv = CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(16))
            val cipherText = korlibs.crypto.AES.encryptAesCbc(plaintext, key.encoded, iv, korlibs.crypto.Padding.PKCS7Padding)
            IVCipherText(iv, cipherText)
        } else if (key is PublicKey) {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plaintext)
            IVCipherText(ByteArray(16), cipherText) // RSA doesn't need IV but IVCipherText requires one
        } else {
            throw IllegalArgumentException("Unsupported key type for encryption on desktop")
        }
    }

    override var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        val iv = CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(16))
        val cipherText = korlibs.crypto.AES.encryptAesCbc(plaintext, masterKey.values, iv, korlibs.crypto.Padding.PKCS7Padding)
        IVCipherText(iv, cipherText)
    }

    companion object {
        private var loadedPrivateKey: PrivateKey? = null
        private var loadedPublicKey: PublicKey? = null

        fun getLoadedPrivateKey(): PrivateKey? = loadedPrivateKey
        fun getLoadedPublicKey(): PublicKey? = loadedPublicKey

        fun setLoadedKeys(priv: PrivateKey, pub: PublicKey) {
            loadedPrivateKey = priv
            loadedPublicKey = pub
        }

        fun encryptWithDPAPI(data: ByteArray): ByteArray {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            return if (isWindows) {
                com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data)
            } else {
                data
            }
        }

        fun decryptWithDPAPI(data: ByteArray): ByteArray {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            return if (isWindows) {
                com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data)
            } else {
                data
            }
        }

        fun importExistingEncryptedMasterKey(
            saltedPassword: SaltedPassword,
            ivSecretKey: IVCipherText
        ): KeyStore {
            val derivedKey = generatePBKDF2AESKey(
                saltedPassword.salt,
                KEY_ITERATION_COUNT,
                saltedPassword.password,
                KEY_LENGTH_BITS
            )
            val decrypted = decryptMasterKey(derivedKey, ivSecretKey)
            
            // Reconstruct public/private keys if they were loaded, otherwise generate a mock pair
            val privKey = loadedPrivateKey ?: generateMockPrivateKey()
            val pubKey = loadedPublicKey ?: generateMockPublicKey()

            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                val helper = CNGKeyStoreHelper(decrypted.values, privKey, pubKey)
                KeyStoreHelperFactory.provideKeyStoreHelper = helper
            } else {
                val helper = KeyStoreHelper(decrypted, privKey, pubKey)
                KeyStoreHelperFactory.provideKeyStoreHelper = helper
            }

            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)
            return ks
        }

        fun createNewKey(password: Password): Pair<Salt, IVCipherText> {
            val salt = Salt(CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(64 / 8)))
            val derivedKey = generatePBKDF2AESKey(
                salt,
                KEY_ITERATION_COUNT,
                password,
                KEY_LENGTH_BITS
            )
            val (unencryptedKey, cipheredKey) = makeFreshNewKey(
                KEY_LENGTH_BITS,
                derivedKey
            )

            // Generate TPM-TEE public-private key pair replica
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val pair = keyPairGen.generateKeyPair()
            loadedPrivateKey = pair.private
            loadedPublicKey = pair.public

            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                val helper = CNGKeyStoreHelper(unencryptedKey.values, pair.private, pair.public)
                KeyStoreHelperFactory.provideKeyStoreHelper = helper
            } else {
                val helper = KeyStoreHelper(unencryptedKey, pair.private, pair.public)
                KeyStoreHelperFactory.provideKeyStoreHelper = helper
            }

            return Pair(salt, cipheredKey)
        }

        private fun generateMockPrivateKey(): PrivateKey {
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            return keyPairGen.generateKeyPair().private
        }

        private fun generateMockPublicKey(): PublicKey {
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            return keyPairGen.generateKeyPair().public
        }
    }
}
