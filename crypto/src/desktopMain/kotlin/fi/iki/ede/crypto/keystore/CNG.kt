package fi.iki.ede.crypto.keystore

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

interface NCrypt : StdCallLibrary {
    companion object {
        val INSTANCE: NCrypt by lazy {
            Native.load("ncrypt", NCrypt::class.java)
        }

        // Provider Names
        val MS_KEY_STORAGE_PROVIDER = WString("Microsoft Software Key Storage Provider")
        val MS_PLATFORM_KEY_STORAGE_PROVIDER = WString("Microsoft Platform Crypto Provider")

        // Algorithms
        const val BCRYPT_AES_ALGORITHM = "AES"

        // Blob types
        val BCRYPT_KEY_DATA_BLOB = WString("KeyDataBlob")
        val NCRYPT_CIPHER_KEY_BLOB = WString("CipherKeyBlob")

        // Properties
        val NCRYPT_CHAINING_MODE_PROPERTY = WString("Chaining Mode")
        val NCRYPT_INITIALIZATION_VECTOR = WString("IV")

        // Chaining Modes
        val BCRYPT_CHAIN_MODE_CBC = WString("ChainingModeCBC")

        // Magic constants
        const val BCRYPT_KEY_DATA_BLOB_MAGIC = 0x4d42444b // "KDBM"
        const val BCRYPT_KEY_DATA_BLOB_VERSION1 = 1
        const val NCRYPT_CIPHER_KEY_BLOB_MAGIC = 0x52485043 // "CPHR"

        // Error codes
        const val ERROR_SUCCESS = 0
    }

    fun NCryptOpenStorageProvider(
        phProvider: PointerByReference,
        pszProviderName: WString?,
        dwFlags: Int
    ): Int

    fun NCryptImportKey(
        hProvider: Pointer,
        hImportKey: Pointer?,
        pszBlobType: WString,
        pParameterList: Pointer?,
        phKey: PointerByReference,
        pbData: ByteArray,
        cbData: Int,
        dwFlags: Int
    ): Int

    fun NCryptEncrypt(
        hKey: Pointer,
        pbInput: ByteArray?,
        cbInput: Int,
        pPaddingInfo: Pointer?,
        pbOutput: ByteArray?,
        cbOutput: Int,
        pcbResult: IntByReference,
        dwFlags: Int
    ): Int

    fun NCryptDecrypt(
        hKey: Pointer,
        pbInput: ByteArray?,
        cbInput: Int,
        pPaddingInfo: Pointer?,
        pbOutput: ByteArray?,
        cbOutput: Int,
        pcbResult: IntByReference,
        dwFlags: Int
    ): Int

    fun NCryptSetProperty(
        hObject: Pointer,
        pszProperty: WString,
        pbInput: Pointer,
        cbInput: Int,
        dwFlags: Int
    ): Int

    fun NCryptFreeObject(
        hObject: Pointer
    ): Int

    fun NCryptDeleteKey(
        hKey: Pointer,
        dwFlags: Int
    ): Int
}
