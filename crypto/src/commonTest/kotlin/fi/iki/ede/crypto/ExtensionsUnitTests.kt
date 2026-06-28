package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class ExtensionsUnitTests {

    @Test
    fun `Password_encrypt should call encrypter with utf8 bytes`() {
        val passwordChars = charArrayOf('p', 'a', 's', 's')
        val password = Password(passwordChars)
        val expectedResult = IVCipherText(byteArrayOf(1), byteArrayOf(2))

        var capturedBytes: ByteArray? = null
        val encrypter: (ByteArray) -> IVCipherText = {
            capturedBytes = it
            expectedResult
        }

        val result = password.encrypt(encrypter)

        assertEquals(expectedResult, result)
        assertContentEquals("pass".encodeToByteArray(), capturedBytes)
    }

    @Test
    fun `String_encrypt should call encrypter with trimmed string bytes`() {
        val input = "  text to encrypt  "
        val expectedResult = IVCipherText(byteArrayOf(3), byteArrayOf(4))

        var capturedBytes: ByteArray? = null
        val encrypter: (ByteArray) -> IVCipherText = {
            capturedBytes = it
            expectedResult
        }

        val result = input.encrypt(encrypter)

        assertEquals(expectedResult, result)
        assertContentEquals("text to encrypt".encodeToByteArray(), capturedBytes)
    }

    @Test
    fun `IVCipherText_decrypt should return string from decrypted bytes`() {
        val ivCipherText = IVCipherText(byteArrayOf(1), byteArrayOf(2))
        val expectedDecryptedBytes = "decrypted".encodeToByteArray()

        val decrypter: (IVCipherText) -> ByteArray = {
            assertEquals(ivCipherText, it)
            expectedDecryptedBytes
        }

        val result = ivCipherText.decrypt(decrypter)

        assertEquals("decrypted", result)
    }
}