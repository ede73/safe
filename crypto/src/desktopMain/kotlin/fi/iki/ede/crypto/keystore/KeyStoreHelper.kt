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
import java.security.KeyPair
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
import korlibs.crypto.AES
import korlibs.crypto.Padding

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
            AES.decryptAesCbc(encrypted.cipherText, key.encoded, encrypted.iv, Padding.PKCS7Padding)
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
            AES.decryptAesCbc(encrypted.cipherText, masterKey.values, encrypted.iv, Padding.PKCS7Padding)
        }
    }

    override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { plaintext, key ->
        if (key is SecretKeySpec) {
            val iv = CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(16))
            val cipherText = AES.encryptAesCbc(plaintext, key.encoded, iv, Padding.PKCS7Padding)
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
        val cipherText = AES.encryptAesCbc(plaintext, masterKey.values, iv, Padding.PKCS7Padding)
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

        private fun generateMockKeyPair(): KeyPair {
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            return keyPairGen.generateKeyPair()
        }

        fun ensureMockKeysLoaded() {
            if (loadedPrivateKey == null || loadedPublicKey == null) {
                val pair = generateMockKeyPair()
                loadedPrivateKey = pair.private
                loadedPublicKey = pair.public
            }
        }

        private fun initializeKeyStoreHelper(
            masterKey: KMPSecretKeySpec,
            privKey: PrivateKey,
            pubKey: PublicKey
        ) {
            if (PlatformUtils.isWindows) {
                KeyStoreHelperFactory.provideKeyStoreHelper = CNGKeyStoreHelper(masterKey.values)
            } else {
                KeyStoreHelperFactory.provideKeyStoreHelper = KeyStoreHelper(
                    masterKey,
                    privKey,
                    pubKey
                )
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
            
            ensureMockKeysLoaded()
            val privKey = loadedPrivateKey!!
            val pubKey = loadedPublicKey!!

            initializeKeyStoreHelper(decrypted, privKey, pubKey)

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
            val pair = generateMockKeyPair()
            loadedPrivateKey = pair.private
            loadedPublicKey = pair.public

            initializeKeyStoreHelper(unencryptedKey, pair.private, pair.public)

            return Pair(salt, cipheredKey)
        }
    }
}
