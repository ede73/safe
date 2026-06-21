package fi.iki.ede.crypto.keystore

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.NCrypt
object PlatformUtils {
    val isWindows: Boolean by lazy {
        System.getProperty("os.name").lowercase().contains("windows")
    }

    fun encryptWithDPAPI(data: ByteArray): ByteArray {
        return if (isWindows) {
            Crypt32Util.cryptProtectData(data)
        } else {
            data
        }
    }

    fun decryptWithDPAPI(data: ByteArray): ByteArray {
        return if (isWindows) {
            Crypt32Util.cryptUnprotectData(data)
        } else {
            data
        }
    }

    private const val NCRYPT_PAD_PKCS1_FLAG = 0x00000002

    private fun getProviderHandle(): Pointer {
        val phProvider = PointerByReference()
        var status = NCrypt.INSTANCE.NCryptOpenStorageProvider(phProvider, NCrypt.MS_PLATFORM_KEY_STORAGE_PROVIDER, 0)
        if (status != NCrypt.ERROR_SUCCESS) {
            status = NCrypt.INSTANCE.NCryptOpenStorageProvider(phProvider, NCrypt.MS_KEY_STORAGE_PROVIDER, 0)
        }
        if (status != NCrypt.ERROR_SUCCESS) {
            throw RuntimeException("Failed to open NCrypt storage provider. Status: $status")
        }
        return phProvider.value
    }

    fun deleteCNGPersistedKey(alias: String) {
        if (!isWindows) return
        try {
            val hProvider = getProviderHandle()
            val phKey = PointerByReference()
            val status = NCrypt.INSTANCE.NCryptOpenKey(hProvider, phKey, WString(alias), 0, 0)
            if (status == NCrypt.ERROR_SUCCESS) {
                NCrypt.INSTANCE.NCryptDeleteKey(phKey.value, 0)
            }
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getOrCreatePersistedKey(alias: String): Pointer {
        val hProvider = getProviderHandle()
        val phKey = PointerByReference()
        var status = NCrypt.INSTANCE.NCryptOpenKey(hProvider, phKey, WString(alias), 0, 0)
        if (status == NCrypt.ERROR_SUCCESS) {
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
            return phKey.value
        }

        // Create the persisted key
        status = NCrypt.INSTANCE.NCryptCreatePersistedKey(
            hProvider,
            phKey,
            WString("RSA"),
            WString(alias),
            0,
            0
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
            throw RuntimeException("Failed to create persisted key. Status: $status")
        }

        val keyLength = 2048
        val keyLengthMem = Memory(4)
        keyLengthMem.setInt(0, keyLength)
        status = NCrypt.INSTANCE.NCryptSetProperty(
            phKey.value,
            WString("Length"),
            keyLengthMem,
            4,
            0
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            NCrypt.INSTANCE.NCryptFreeObject(phKey.value)
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
            throw RuntimeException("Failed to set key length. Status: $status")
        }

        status = NCrypt.INSTANCE.NCryptFinalizeKey(phKey.value, 0)
        if (status != NCrypt.ERROR_SUCCESS) {
            NCrypt.INSTANCE.NCryptFreeObject(phKey.value)
            NCrypt.INSTANCE.NCryptFreeObject(hProvider)
            throw RuntimeException("Failed to finalize key. Status: $status")
        }

        NCrypt.INSTANCE.NCryptFreeObject(hProvider)
        return phKey.value
    }

    fun encryptData(hKey: Pointer, data: ByteArray, flags: Int): ByteArray {
        val pcbResult = IntByReference()
        var status = NCrypt.INSTANCE.NCryptEncrypt(
            hKey,
            data,
            data.size,
            null,
            null,
            0,
            pcbResult,
            flags
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            throw RuntimeException("NCryptEncrypt size query failed. Status: $status")
        }
        val output = ByteArray(pcbResult.value)
        status = NCrypt.INSTANCE.NCryptEncrypt(
            hKey,
            data,
            data.size,
            null,
            output,
            output.size,
            pcbResult,
            flags
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            throw RuntimeException("NCryptEncrypt failed. Status: $status")
        }
        return output
    }

    fun decryptData(hKey: Pointer, data: ByteArray, flags: Int): ByteArray {
        val pcbResult = IntByReference()
        var status = NCrypt.INSTANCE.NCryptDecrypt(
            hKey,
            data,
            data.size,
            null,
            null,
            0,
            pcbResult,
            flags
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            throw RuntimeException("NCryptDecrypt size query failed. Status: $status")
        }
        val output = ByteArray(pcbResult.value)
        status = NCrypt.INSTANCE.NCryptDecrypt(
            hKey,
            data,
            data.size,
            null,
            output,
            output.size,
            pcbResult,
            flags
        )
        if (status != NCrypt.ERROR_SUCCESS) {
            throw RuntimeException("NCryptDecrypt failed. Status: $status")
        }
        return output
    }

    fun encryptWithCNG(data: ByteArray, alias: String): ByteArray {
        val hKey = getOrCreatePersistedKey(alias)
        try {
            return encryptData(hKey, data, NCRYPT_PAD_PKCS1_FLAG)
        } finally {
            NCrypt.INSTANCE.NCryptFreeObject(hKey)
        }
    }

    fun decryptWithCNG(encrypted: IVCipherText, alias: String): ByteArray {
        val hKey = getOrCreatePersistedKey(alias)
        try {
            return decryptData(hKey, encrypted.cipherText, NCRYPT_PAD_PKCS1_FLAG)
        } finally {
            NCrypt.INSTANCE.NCryptFreeObject(hKey)
        }
    }
}
