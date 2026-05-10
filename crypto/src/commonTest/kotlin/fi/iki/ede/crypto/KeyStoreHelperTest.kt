package fi.iki.ede.crypto.keystore

import fi.iki.ede.crypto.IVCipherText
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyStore

class KeyStoreHelperTest {

    private lateinit var keyStoreHelper: KeyStoreHelper

    // Test Vectors
    private val V1_KEY = KMPSecretKeySpec(
        "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4".hexToByteArray()
    )
    private val V1_IV = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
    private val V1_PLAINTEXT = "6bc1bee22e409f96e93d7e117393172a".hexToByteArray()
    private val V1_CIPHERTEXT =
        "f58c4c04d6e5f1ba779eabfb5f7bfbd6485a5c81519cf378fa36d42b8547edc0".hexToByteArray()

    private val V2_KEY = KMPSecretKeySpec(ByteArray(32) { 0x42.toByte() })
    private val V2_IV = ByteArray(16) { 0x11.toByte() }
    private val V2_PLAINTEXT =
        "This is a plaintext message that will require padding.".toByteArray()
    private val V2_CIPHERTEXT =
        "b5467830be36282f104292e0ad094e8a17581c84f34feea917bb2a5d863c5d4387603644a5ef9c39f2bccc01973f494e6ef248beb5d40375fc6535b790b9cfb4".hexToByteArray()

    private val V3_KEY = KMPSecretKeySpec(ByteArray(32) { 0x99.toByte() })
    private val V3_IV = ByteArray(16) { 0xfe.toByte() }
    private val V3_PLAINTEXT = "1234567890abcdef".toByteArray()
    private val V3_CIPHERTEXT =
        "c6f33d7228be225c23984ba707650a42c0d399e38459e317d14c6984b2db7656".hexToByteArray()

    @BeforeEach
    fun setUp() {
        // Mock the static KeyStore.getInstance() method, which fails on the JVM.
        mockkStatic(KeyStore::class)
        val mockKeyStore = mockk<KeyStore>()
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore

        // Define behavior for the methods called in the KeyStoreHelper constructor.
        every { mockKeyStore.load(any()) } returns Unit
        every { mockKeyStore.containsAlias(any()) } returns true

        // Now it is safe to create a real KeyStoreHelper object and spy on it.
        keyStoreHelper = spyk(KeyStoreHelper(mockKeyStore)) {
            //every { getAESCipher() } returns testCipher
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up the static mocks after each test to avoid test leakage.
        unmockkStatic(KeyStore::class)
    }

    @Test
    fun `encryptByteArray with empty input gives empty result`() {
        val result = keyStoreHelper.encryptByteArray(byteArrayOf(), V1_KEY)
        assertEquals(0, result.iv.size)
        assertEquals(0, result.cipherText.size)
    }


    @Test
    fun `decryptByteArray with empty input gives empty result`() {
        val result = keyStoreHelper.decryptByteArray(IVCipherText.getEmpty(), V1_KEY)
        assertEquals(0, result.size)
    }

    @Test
    fun `encryptByteArray returns correct ciphertext for NIST vector`() {
        mockkObject(CipherUtilities.Companion)
        every { CipherUtilities.generateRandomBytes(any<CipherUtilities.Companion.Bytes>()) } returns V1_IV
        val result = keyStoreHelper.encryptByteArray(V1_PLAINTEXT, V1_KEY)
        assertArrayEquals(V1_IV, result.iv)
        assertArrayEquals(V1_CIPHERTEXT, result.cipherText)

        unmockkObject(CipherUtilities.Companion)
    }

    @Test
    fun `decryptByteArray returns correct plaintext for NIST vector`() {
        val result = keyStoreHelper.decryptByteArray(IVCipherText(V1_IV, V1_CIPHERTEXT), V1_KEY)
        assertArrayEquals(V1_PLAINTEXT, result)
    }

    @Test
    fun `encryptByteArray returns correct ciphertext for custom vector 2`() {
        mockkObject(CipherUtilities.Companion)
        every { CipherUtilities.generateRandomBytes(any<CipherUtilities.Companion.Bytes>()) } returns V2_IV

        val result = keyStoreHelper.encryptByteArray(V2_PLAINTEXT, V2_KEY)
        assertArrayEquals(V2_IV, result.iv)
        assertArrayEquals(V2_CIPHERTEXT, result.cipherText)

        unmockkObject(CipherUtilities.Companion)
    }

    @Test
    fun `decryptByteArray returns correct plaintext for custom vector 2`() {
        val result = keyStoreHelper.decryptByteArray(IVCipherText(V2_IV, V2_CIPHERTEXT), V2_KEY)
        assertArrayEquals(V2_PLAINTEXT, result)
    }

    @Test
    fun `encryptByteArray returns correct ciphertext for single-block vector 3`() {
        mockkObject(CipherUtilities.Companion)
        every { CipherUtilities.generateRandomBytes(any<CipherUtilities.Companion.Bytes>()) } returns V3_IV

        val result = keyStoreHelper.encryptByteArray(V3_PLAINTEXT, V3_KEY)
        assertArrayEquals(V3_IV, result.iv)
        assertArrayEquals(V3_CIPHERTEXT, result.cipherText)

        unmockkObject(CipherUtilities.Companion)
    }

    @Test
    fun `decryptByteArray returns correct plaintext for single-block vector 3`() {
        val result = keyStoreHelper.decryptByteArray(IVCipherText(V3_IV, V3_CIPHERTEXT), V3_KEY)
        assertArrayEquals(V3_PLAINTEXT, result)
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}