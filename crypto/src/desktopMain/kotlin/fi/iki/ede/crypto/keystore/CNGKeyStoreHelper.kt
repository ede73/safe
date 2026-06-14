package fi.iki.ede.crypto.keystore

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.NCrypt.Companion.BCRYPT_AES_ALGORITHM
import fi.iki.ede.crypto.keystore.NCrypt.Companion.BCRYPT_CHAIN_MODE_CBC
import fi.iki.ede.crypto.keystore.NCrypt.Companion.NCRYPT_CIPHER_KEY_BLOB
import fi.iki.ede.crypto.keystore.NCrypt.Companion.NCRYPT_CIPHER_KEY_BLOB_MAGIC
import fi.iki.ede.crypto.keystore.NCrypt.Companion.ERROR_SUCCESS
import fi.iki.ede.crypto.keystore.NCrypt.Companion.MS_KEY_STORAGE_PROVIDER
import fi.iki.ede.crypto.keystore.NCrypt.Companion.MS_PLATFORM_KEY_STORAGE_PROVIDER
import fi.iki.ede.crypto.keystore.NCrypt.Companion.NCRYPT_CHAINING_MODE_PROPERTY
import fi.iki.ede.crypto.keystore.NCrypt.Companion.NCRYPT_INITIALIZATION_VECTOR
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyPairGenerator
import fi.iki.ede.logger.Logger

class CNGKeyStoreHelper(
    masterKeyBytes: ByteArray,
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey
) : IKeyStoreHelper {

    private val masterKey = masterKeyBytes.clone()
    private val hKey: Pointer
    private val lock = Any()

    init {
        // Build the NCRYPT_CIPHER_KEY_BLOB which wraps a BCRYPT_KEY_DATA_BLOB
        // Header: cbSize (16) + dwMagic (0x52485043) + cbAlgName (8) + cbKeyData (44)
        // Followed by Algorithm Name string L"AES" (8 bytes: 'A',0,'E',0,'S',0,0,0)
        // Followed by BCRYPT_KEY_DATA_BLOB_HEADER (12 bytes): dwMagic (0x4d42444b) + dwVersion (1) + cbKeyData (32)
        // Followed by raw AES key bytes (32 bytes)
        val headerSize = 16
        val algNameBytes = byteArrayOf(
            'A'.code.toByte(), 0,
            'E'.code.toByte(), 0,
            'S'.code.toByte(), 0,
            0, 0
        )
        val bcryptHeaderSize = 12
        val keyDataSize = masterKeyBytes.size
        val ncryptKeyDataSize = bcryptHeaderSize + keyDataSize
        val blobBytes = ByteArray(headerSize + algNameBytes.size + ncryptKeyDataSize)

        val buffer = ByteBuffer.wrap(blobBytes).order(ByteOrder.LITTLE_ENDIAN)
        // NCRYPT_KEY_BLOB_HEADER
        buffer.putInt(headerSize)
        buffer.putInt(NCRYPT_CIPHER_KEY_BLOB_MAGIC)
        buffer.putInt(algNameBytes.size)
        buffer.putInt(ncryptKeyDataSize)
        // Algorithm Name
        buffer.put(algNameBytes)
        // BCRYPT_KEY_DATA_BLOB_HEADER
        buffer.putInt(NCrypt.BCRYPT_KEY_DATA_BLOB_MAGIC)
        buffer.putInt(NCrypt.BCRYPT_KEY_DATA_BLOB_VERSION1)
        buffer.putInt(keyDataSize)
        // Raw Key Data
        buffer.put(masterKeyBytes)

        // Try to import the key
        var providerPointer: Pointer? = null
        var keyPointer: Pointer? = null

        // 1. Try Platform Crypto Provider (TPM)
        var status = openProviderAndImportKey(
            MS_PLATFORM_KEY_STORAGE_PROVIDER,
            blobBytes,
            { prov, key ->
                providerPointer = prov
                keyPointer = key
            }
        )

        // 2. Fallback to Software Key Storage Provider (LSASS/DPAPI)
        if (status != ERROR_SUCCESS || keyPointer == null) {
            Logger.i("CNGKeyStoreHelper", "TPM KSP not available or doesn't support symmetric keys. Falling back to Software KSP. status=$status")
            status = openProviderAndImportKey(
                MS_KEY_STORAGE_PROVIDER,
                blobBytes,
                { prov, key ->
                    providerPointer = prov
                    keyPointer = key
                }
            )
        }

        // Overwrite the temporary key buffers to clear them from JVM memory
        blobBytes.fill(0)
        masterKeyBytes.fill(0)

        if (status != ERROR_SUCCESS || keyPointer == null) {
            throw RuntimeException("Failed to import master key into Windows CNG. Status: $status")
        }

        hKey = keyPointer

        // Clean up the provider pointer since key handle is now independent
        providerPointer?.let {
            NCrypt.INSTANCE.NCryptFreeObject(it)
        }

        // Configure the key: Set chaining mode to CBC
        val chainModeWStr = BCRYPT_CHAIN_MODE_CBC
        val chainModeMem = Memory((chainModeWStr.length + 1) * 2L)
        chainModeMem.setWideString(0, chainModeWStr.toString())
        status = NCrypt.INSTANCE.NCryptSetProperty(
            hKey,
            NCRYPT_CHAINING_MODE_PROPERTY,
            chainModeMem,
            chainModeMem.size().toInt(),
            0
        )
        if (status != ERROR_SUCCESS) {
            NCrypt.INSTANCE.NCryptFreeObject(hKey)
            throw RuntimeException("Failed to set CBC chaining mode on CNG key. Status: $status")
        }
    }

    private fun openProviderAndImportKey(
        providerName: com.sun.jna.WString,
        blobBytes: ByteArray,
        onSuccess: (Pointer, Pointer) -> Unit
    ): Int {
        val phProvider = PointerByReference()
        var status = NCrypt.INSTANCE.NCryptOpenStorageProvider(phProvider, providerName, 0)
        if (status != ERROR_SUCCESS) {
            return status
        }
        val hProvider = phProvider.value

        val phKey = PointerByReference()
        status = NCrypt.INSTANCE.NCryptImportKey(
            hProvider,
            null,
            NCRYPT_CIPHER_KEY_BLOB,
            null,
            phKey,
            blobBytes,
            blobBytes.size,
            0
        )

        if (status == ERROR_SUCCESS) {
            onSuccess(hProvider, phKey.value)
        } else {
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
        }
        return status
    }

    protected fun finalize() {
        NCrypt.INSTANCE.NCryptFreeObject(hKey)
    }

    override fun testingDeleteKeys_DO_NOT_USE() {
        // No-op for session-based key
    }

    override fun rotateKeys() {
        throw NotImplementedError("Key rotation is not supported on desktop")
    }

    override fun getOrCreateBiokey(): KMPKey {
        return privateKey
    }

    override var decrypterProviderWithKey: (IVCipherText, KMPKey) -> ByteArray = { encrypted, key ->
        if (encrypted.iv.isEmpty()) {
            byteArrayOf()
        } else if (key is javax.crypto.spec.SecretKeySpec) {
            korlibs.crypto.AES.decryptAesCbc(encrypted.cipherText, key.encoded, encrypted.iv, korlibs.crypto.Padding.PKCS7Padding)
        } else if (key is PrivateKey) {
            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key)
            cipher.doFinal(encrypted.cipherText)
        } else {
            // For other keys, fallback to regular decryption
            decrypterProvider(encrypted)
        }
    }

    override var decrypterProvider: (IVCipherText) -> ByteArray = { encrypted ->
        if (encrypted.iv.isEmpty()) {
            byteArrayOf()
        } else {
            korlibs.crypto.AES.decryptAesCbc(encrypted.cipherText, masterKey, encrypted.iv, korlibs.crypto.Padding.PKCS7Padding)
        }
    }

    override var encrypterProviderWithKey: (ByteArray, KMPKey) -> IVCipherText = { plaintext, key ->
        if (key is javax.crypto.spec.SecretKeySpec) {
            val iv = CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(16))
            val cipherText = korlibs.crypto.AES.encryptAesCbc(plaintext, key.encoded, iv, korlibs.crypto.Padding.PKCS7Padding)
            IVCipherText(iv, cipherText)
        } else if (key is PublicKey) {
            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plaintext)
            IVCipherText(ByteArray(16), cipherText)
        } else {
            encrypterProvider(plaintext)
        }
    }

    override var encrypterProvider: (ByteArray) -> IVCipherText = { plaintext ->
        val iv = CipherUtilities.generateRandomBytes(CipherUtilities.Companion.Bytes(16))
        val cipherText = korlibs.crypto.AES.encryptAesCbc(plaintext, masterKey, iv, korlibs.crypto.Padding.PKCS7Padding)
        IVCipherText(iv, cipherText)
    }

    private fun padPKCS7(input: ByteArray): ByteArray {
        val paddingLength = 16 - (input.size % 16)
        val padded = ByteArray(input.size + paddingLength)
        System.arraycopy(input, 0, padded, 0, input.size)
        for (i in input.size until padded.size) {
            padded[i] = paddingLength.toByte()
        }
        return padded
    }

    private fun unpadPKCS7(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        val paddingLength = input.last().toInt() and 0xFF
        if (paddingLength <= 0 || paddingLength > 16) {
            throw IllegalArgumentException("Invalid PKCS7 padding length: $paddingLength")
        }
        for (i in (input.size - paddingLength) until input.size) {
            if (input[i].toInt() and 0xFF != paddingLength) {
                throw IllegalArgumentException("Invalid PKCS7 padding byte at index $i")
            }
        }
        val unpadded = ByteArray(input.size - paddingLength)
        System.arraycopy(input, 0, unpadded, 0, unpadded.size)
        return unpadded
    }
}
