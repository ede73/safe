package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KMPSecretKeySpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.security.KeyStore
import javax.crypto.Cipher
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyManagementTest {
    @Test
    fun `generateAESKey creates key with correct length`() {
        val key128 = KeyManagement.generateAESKey(128)
        assertEquals(16, key128.size)

        val key256 = KeyManagement.generateAESKey(256)
        assertEquals(32, key256.size)
    }

    @Test
    fun `generatePBKDF2AESKey works with valid inputs`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(8) { it.toByte() })
        val key = KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 256)
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `generatePBKDF2AESKey throws for wrong salt size`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(7))
        assertThrows<IllegalArgumentException> {
            KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 256)
        }
    }

    @Test
    fun `generatePBKDF2AESKey throws for unsupported key length`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(8))
        assertThrows<IllegalArgumentException> {
            KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 128)
        }
    }

    @Test
    fun `encrypt and decrypt master key`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(8) { it.toByte() })
        val pbkdf2key = KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 256)

        val masterKeyToEncrypt = KeyManagement.generateAESKey(256)

        val encryptedMasterKey = KeyManagement.encryptMasterKey(pbkdf2key, masterKeyToEncrypt)

        val decryptedMasterKeySpec = KeyManagement.decryptMasterKey(pbkdf2key, encryptedMasterKey)

        assertArrayEquals(masterKeyToEncrypt, decryptedMasterKeySpec.encoded)
    }

    @Test
    fun `makeFreshNewKey generates and encrypts a new key`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(8) { it.toByte() })
        val pbkdf2key = KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 256)

        val (newMasterKey, encryptedNewMasterKey) = KeyManagement.makeFreshNewKey(256, pbkdf2key)

        val decryptedMasterKey = KeyManagement.decryptMasterKey(pbkdf2key, encryptedNewMasterKey)

        assertEquals("AES", newMasterKey.algorithm)
        assertEquals(32, newMasterKey.encoded.size)
        assertArrayEquals(newMasterKey.encoded, decryptedMasterKey.encoded)
    }

    @Test
    fun `decryptMasterKey throws with wrong IV size`() {
        val password = Password("password".toCharArray())
        val salt = Salt(ByteArray(8) { it.toByte() })
        val pbkdf2key = KeyManagement.generatePBKDF2AESKey(salt, 1000, password, 256)

        val masterKeyToEncrypt = KeyManagement.generateAESKey(256)
        val encryptedMasterKey = KeyManagement.encryptMasterKey(pbkdf2key, masterKeyToEncrypt)

        // Create a bad IV
        val badIVCipherText = IVCipherText(ByteArray(15), encryptedMasterKey.cipherText)

        assertThrows<IllegalArgumentException> {
            KeyManagement.decryptMasterKey(pbkdf2key, badIVCipherText)
        }
    }

    @Test
    fun `getAESCipher prefers PKCS7Padding`() {
        mockkStatic(KeyStore::class)
        val mockKeyStore = mockk<KeyStore>()
        every { mockKeyStore.load(any()) } returns Unit
        every { mockKeyStore.containsAlias(any()) } returns true
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        val keyStoreHelper = KeyStoreHelper(mockKeyStore)

        mockkStatic(Cipher::class)
        val mockCipher = mockk<Cipher>(relaxed = true)
        every { mockCipher.blockSize } returns 16
        every { mockCipher.iv } returns ByteArray(16)

        val pkcs7Mode = "AES/CBC/PKCS7Padding"
        every { Cipher.getInstance(pkcs7Mode) } returns mockCipher
        every { Cipher.getInstance("AES/CBC/PKCS5Padding") } throws Exception("Should not be called")

        val key = KMPSecretKeySpec(ByteArray(32))
        keyStoreHelper.encryptByteArray(ByteArray(16), key)

        verify { Cipher.getInstance(pkcs7Mode) }
        verify(exactly = 0) { Cipher.getInstance("AES/CBC/PKCS5Padding") }
        unmockkStatic(Cipher::class)
        unmockkStatic(KeyStore::class)
    }
}
