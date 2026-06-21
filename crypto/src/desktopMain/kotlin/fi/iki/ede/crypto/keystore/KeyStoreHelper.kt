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
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import fi.iki.ede.crypto.keystore.NCrypt.Companion.MS_KEY_STORAGE_PROVIDER
import fi.iki.ede.crypto.keystore.NCrypt.Companion.MS_PLATFORM_KEY_STORAGE_PROVIDER
import fi.iki.ede.crypto.keystore.NCrypt.Companion.ERROR_SUCCESS
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
        private var biometricEncryptedMasterKey: IVCipherText? = null

        fun getLoadedPrivateKey(): PrivateKey? = loadedPrivateKey
        fun getLoadedPublicKey(): PublicKey? = loadedPublicKey
        fun getBiometricEncryptedMasterKey(): IVCipherText? = biometricEncryptedMasterKey

        fun setLoadedKeys(priv: PrivateKey, pub: PublicKey) {
            loadedPrivateKey = priv
            loadedPublicKey = pub
        }

        fun generateMockKeyPair(): KeyPair {
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

        private fun getBiometricKeyHandle(createIfNeeded: Boolean): Pointer? {
            val phProvider = PointerByReference()
            var status = NCrypt.INSTANCE.NCryptOpenStorageProvider(
                phProvider,
                MS_PLATFORM_KEY_STORAGE_PROVIDER,
                0
            )
            if (status != ERROR_SUCCESS) {
                status = NCrypt.INSTANCE.NCryptOpenStorageProvider(
                    phProvider,
                    MS_KEY_STORAGE_PROVIDER,
                    0
                )
            }
            if (status != ERROR_SUCCESS) {
                return null
            }
            val hProvider = phProvider.value

            val phKey = PointerByReference()
            val keyName = WString("SafeBiometricKey")
            
            status = NCrypt.INSTANCE.NCryptOpenKey(
                hProvider,
                phKey,
                keyName,
                0,
                0
            )
            
            if (status != ERROR_SUCCESS && createIfNeeded) {
                status = NCrypt.INSTANCE.NCryptCreatePersistedKey(
                    hProvider,
                    phKey,
                    WString("RSA"),
                    keyName,
                    0,
                    0
                )
                if (status == ERROR_SUCCESS) {
                    status = NCrypt.INSTANCE.NCryptFinalizeKey(phKey.value, 0)
                    if (status != ERROR_SUCCESS) {
                        NCrypt.INSTANCE.NCryptFreeObject(phKey.value)
                        NCrypt.INSTANCE.NCryptFreeObject(hProvider)
                        return null
                    }
                }
            }
            
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
            return if (status == ERROR_SUCCESS) phKey.value else null
        }

        private fun generateCNGBiometricKeyIfNeeded() {
            val handle = getBiometricKeyHandle(true)
            if (handle != null) {
                NCrypt.INSTANCE.NCryptFreeObject(handle)
            }
        }

        private fun encryptMasterKeyWithCNG(masterKeyBytes: ByteArray): ByteArray {
            val hKey = getBiometricKeyHandle(true) ?: throw RuntimeException("Failed to get CNG biometric key handle")
            try {
                val pcbResult = IntByReference()
                var status = NCrypt.INSTANCE.NCryptEncrypt(
                    hKey,
                    masterKeyBytes,
                    masterKeyBytes.size,
                    null,
                    null,
                    0,
                    pcbResult,
                    2 // NCRYPT_PAD_PKCS1_FLAG
                )
                if (status != ERROR_SUCCESS) {
                    throw RuntimeException("CNG NCryptEncrypt failed to get size: $status")
                }
                val output = ByteArray(pcbResult.value)
                status = NCrypt.INSTANCE.NCryptEncrypt(
                    hKey,
                    masterKeyBytes,
                    masterKeyBytes.size,
                    null,
                    output,
                    output.size,
                    pcbResult,
                    2 // NCRYPT_PAD_PKCS1_FLAG
                )
                if (status != ERROR_SUCCESS) {
                    throw RuntimeException("CNG NCryptEncrypt failed: $status")
                }
                return output
            } finally {
                NCrypt.INSTANCE.NCryptFreeObject(hKey)
            }
        }

        private fun decryptMasterKeyWithCNG(encryptedBytes: ByteArray): ByteArray {
            val hKey = getBiometricKeyHandle(false) ?: throw RuntimeException("Biometric key not found in Windows CNG")
            try {
                val pcbResult = IntByReference()
                var status = NCrypt.INSTANCE.NCryptDecrypt(
                    hKey,
                    encryptedBytes,
                    encryptedBytes.size,
                    null,
                    null,
                    0,
                    pcbResult,
                    2 // NCRYPT_PAD_PKCS1_FLAG
                )
                if (status != ERROR_SUCCESS) {
                    throw RuntimeException("CNG NCryptDecrypt failed to get size: $status")
                }
                val output = ByteArray(pcbResult.value)
                status = NCrypt.INSTANCE.NCryptDecrypt(
                    hKey,
                    encryptedBytes,
                    encryptedBytes.size,
                    null,
                    output,
                    output.size,
                    pcbResult,
                    2 // NCRYPT_PAD_PKCS1_FLAG
                )
                if (status != ERROR_SUCCESS) {
                    throw RuntimeException("CNG NCryptDecrypt failed: $status")
                }
                return output
            } finally {
                NCrypt.INSTANCE.NCryptFreeObject(hKey)
            }
        }

        fun loginWithBiometricKey(
            encryptedMasterKey: IVCipherText
        ): Boolean {
            val isWindows = PlatformUtils.isWindows
            try {
                val decryptedMasterKeyBytes = if (isWindows) {
                    PlatformUtils.decryptWithCNG(encryptedMasterKey, "fi.iki.ede.safe.biometrics")
                } else {
                    val privKey = loadedPrivateKey ?: return false
                    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                    cipher.init(Cipher.DECRYPT_MODE, privKey)
                    cipher.doFinal(encryptedMasterKey.cipherText)
                }
                
                ensureMockKeysLoaded()
                val mockPrivKey = loadedPrivateKey!!
                val mockPubKey = loadedPublicKey!!
                initializeKeyStoreHelper(KMPSecretKeySpec(decryptedMasterKeyBytes), mockPrivKey, mockPubKey)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
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

            // Pre-calculate biometric encrypted master key
            try {
                val isWindows = PlatformUtils.isWindows
                val cipherText = if (isWindows) {
                    PlatformUtils.encryptWithCNG(decrypted.values, "fi.iki.ede.safe.biometrics")
                } else {
                    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
                    cipher.doFinal(decrypted.values)
                }
                biometricEncryptedMasterKey = IVCipherText(ByteArray(16), cipherText)
            } catch (e: Exception) {
                e.printStackTrace()
            }

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

            // Pre-calculate biometric encrypted master key
            try {
                val isWindows = PlatformUtils.isWindows
                val cipherText = if (isWindows) {
                    PlatformUtils.encryptWithCNG(unencryptedKey.values, "fi.iki.ede.safe.biometrics")
                } else {
                    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, pair.public)
                    cipher.doFinal(unencryptedKey.values)
                }
                biometricEncryptedMasterKey = IVCipherText(ByteArray(16), cipherText)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            initializeKeyStoreHelper(unencryptedKey, pair.private, pair.public)

            return Pair(salt, cipheredKey)
        }

    }
}
