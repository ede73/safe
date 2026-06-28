@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.bytes
import fi.iki.ede.crypto.keystore.KeyManagement.decryptMasterKey
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyManagement.makeFreshNewKey
import korlibs.crypto.AES
import korlibs.crypto.Padding
import korlibs.crypto.SecureRandom
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*
import platform.posix.memcpy

class KeyStoreHelper(
    val masterKey: KMPSecretKeySpec
) : IKeyStoreHelper {

    override fun testingDeleteKeys_DO_NOT_USE() {}
    override fun rotateKeys() {}
    override fun getOrCreateBiokey(): KMPKey = object : KMPKey {
        override fun getAlgorithm(): String = "Mock"
        override fun getFormat(): String = "Mock"
        override fun getEncoded(): ByteArray = ByteArray(0)
    }

    override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray = { encrypted, key ->
        if (encrypted.iv.isEmpty() || encrypted.cipherText.isEmpty()) {
            byteArrayOf()
        } else {
            try {
                AES.decryptAesCbc(encrypted.cipherText, key.getEncoded(), encrypted.iv, Padding.PKCS7Padding)
            } catch (e: Throwable) {
                e.printStackTrace()
                byteArrayOf()
            }
        }
    }
    
    override var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        if (encrypted.iv.isEmpty() || encrypted.cipherText.isEmpty()) {
            byteArrayOf()
        } else {
            try {
                AES.decryptAesCbc(encrypted.cipherText, masterKey.values, encrypted.iv, Padding.PKCS7Padding)
            } catch (e: Throwable) {
                e.printStackTrace()
                byteArrayOf()
            }
        }
    }
    
    override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { plaintext, key ->
        val iv = CipherUtilities.generateRandomBytes(CipherUtilities.IV_LENGTH.bytes)
        val cipherText = AES.encryptAesCbc(plaintext, key.getEncoded(), iv, Padding.PKCS7Padding)
        IVCipherText(iv, cipherText)
    }
    
    override var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        val iv = CipherUtilities.generateRandomBytes(CipherUtilities.IV_LENGTH.bytes)
        val cipherText = AES.encryptAesCbc(plaintext, masterKey.values, iv, Padding.PKCS7Padding)
        IVCipherText(iv, cipherText)
    }

    companion object {
        private var biometricEncryptedMasterKey: IVCipherText? = null

        fun getBiometricEncryptedMasterKey(): IVCipherText? = biometricEncryptedMasterKey

        fun createNewKey(password: Password): Pair<Salt, IVCipherText> {
            val saltBytes = ByteArray(8)
            SecureRandom.nextBytes(saltBytes)
            val salt = Salt(saltBytes)
            
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
            
            // Generate Secure Enclave key and encrypt master key
            try {
                biometricEncryptedMasterKey = encryptWithEnclave(unencryptedKey.values)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            KeyStoreHelperFactory.provideKeyStoreHelper = KeyStoreHelper(unencryptedKey)
            return Pair(salt, cipheredKey)
        }

        fun importExistingEncryptedMasterKey(
            saltedPassword: SaltedPassword,
            encryptedKey: IVCipherText
        ) {
            val derivedKey = generatePBKDF2AESKey(
                saltedPassword.salt,
                KEY_ITERATION_COUNT,
                saltedPassword.password,
                KEY_LENGTH_BITS
            )
            
            val decrypted = decryptMasterKey(derivedKey, encryptedKey)

            // Generate Secure Enclave key and encrypt master key
            try {
                biometricEncryptedMasterKey = encryptWithEnclave(decrypted.values)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            KeyStoreHelperFactory.provideKeyStoreHelper = KeyStoreHelper(decrypted)
        }

        fun loginWithBiometricKey(
            encryptedMasterKey: IVCipherText
        ): Boolean {
            return try {
                val decryptedBytes = decryptWithEnclave(encryptedMasterKey.cipherText)
                KeyStoreHelperFactory.provideKeyStoreHelper = KeyStoreHelper(KMPSecretKeySpec(decryptedBytes))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        private fun getOrCreateKey(): SecKeyRef? {
            val tag = "fi.iki.ede.safe.masterkey".encodeToByteArray()
            val tagData = tag.usePinned { pinned ->
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), tag.size.toLong())
            }

            // Query if the key already exists
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            CFDictionarySetValue(query, kSecClass, kSecClassKey)
            CFDictionarySetValue(query, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            CFDictionarySetValue(query, kSecAttrApplicationTag, tagData)
            CFDictionarySetValue(query, kSecReturnRef, kCFBooleanTrue)

            val result = memScoped {
                val resultPtr = alloc<COpaquePointerVar>()
                val status = SecItemCopyMatching(query, resultPtr.ptr.reinterpret())
                if (status == errSecSuccess) {
                    resultPtr.value as SecKeyRef?
                } else {
                    null
                }
            }
            if (result != null) return result

            // Generate new EC key pair
            return memScoped {
                val errorPtr = alloc<COpaquePointerVar>()
                
                val privateKeyAttrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
                CFDictionarySetValue(privateKeyAttrs, kSecAttrIsPermanent, kCFBooleanTrue)
                CFDictionarySetValue(privateKeyAttrs, kSecAttrApplicationTag, tagData)

                val parameters = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
                CFDictionarySetValue(parameters, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                val keySizeVal = alloc<IntVar>().apply { value = 256 }
                val keySizeNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, keySizeVal.ptr)
                CFDictionarySetValue(parameters, kSecAttrKeySizeInBits, keySizeNum)
                CFDictionarySetValue(parameters, kSecAttrTokenID, kSecAttrTokenIDSecureEnclave)
                CFDictionarySetValue(parameters, kSecPrivateKeyAttrs, privateKeyAttrs)

                var key = SecKeyCreateRandomKey(parameters, errorPtr.ptr.reinterpret())
                
                // Fall back to standard CPU key if Secure Enclave is unavailable (e.g. simulator)
                if (key == null) {
                    val fallbackParams = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
                    CFDictionarySetValue(fallbackParams, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
                    CFDictionarySetValue(fallbackParams, kSecAttrKeySizeInBits, keySizeNum)
                    CFDictionarySetValue(fallbackParams, kSecPrivateKeyAttrs, privateKeyAttrs)
                    key = SecKeyCreateRandomKey(fallbackParams, errorPtr.ptr.reinterpret())
                }
                
                key
            }
        }

        private fun encryptWithEnclave(plainText: ByteArray): IVCipherText {
            val key = getOrCreateKey() ?: throw IllegalStateException("Could not get or create secure key")
            val publicKey = SecKeyCopyPublicKey(key) ?: throw IllegalStateException("Could not copy public key")
            
            return memScoped {
                val errorPtr = alloc<COpaquePointerVar>()
                val plainCFData = plainText.toCFData()
                val cipherCFData = SecKeyCreateEncryptedData(
                    publicKey,
                    kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM,
                    plainCFData,
                    errorPtr.ptr.reinterpret()
                ) ?: throw IllegalStateException("SecKeyCreateEncryptedData failed")
                
                val cipherBytes = cipherCFData.toByteArray()
                IVCipherText(ByteArray(16), cipherBytes)
            }
        }

        private fun decryptWithEnclave(cipherText: ByteArray): ByteArray {
            val key = getOrCreateKey() ?: throw IllegalStateException("Could not get or create secure key")
            
            return memScoped {
                val errorPtr = alloc<COpaquePointerVar>()
                val cipherCFData = cipherText.toCFData()
                val plainCFData = SecKeyCreateDecryptedData(
                    key,
                    kSecKeyAlgorithmECIESEncryptionStandardX963SHA256AESGCM,
                    cipherCFData,
                    errorPtr.ptr.reinterpret()
                ) ?: throw IllegalStateException("SecKeyCreateDecryptedData failed")
                
                plainCFData.toByteArray()
            }
        }

        private fun ByteArray.toCFData(): CFDataRef? {
            return this.usePinned { pinned ->
                CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), this.size.toLong())
            }
        }

        private fun CFDataRef.toByteArray(): ByteArray {
            val length = CFDataGetLength(this).toInt()
            val bytesPtr = CFDataGetBytePtr(this) ?: return ByteArray(0)
            val byteArray = ByteArray(length)
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytesPtr, length.toULong())
            }
            return byteArray
        }
    }
}
