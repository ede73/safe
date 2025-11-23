package fi.iki.ede.crypto

import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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

        Assertions.assertEquals(expectedResult, result)
        Assertions.assertArrayEquals("pass".toByteArray(), capturedBytes)
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

        Assertions.assertEquals(expectedResult, result)
        Assertions.assertArrayEquals("text to encrypt".toByteArray(), capturedBytes)
    }

    @Test
    fun `IVCipherText_decrypt should return string from decrypted bytes`() {
        val ivCipherText = IVCipherText(byteArrayOf(1), byteArrayOf(2))
        val expectedDecryptedBytes = "decrypted".toByteArray()

        val decrypter: (IVCipherText) -> ByteArray = {
            Assertions.assertEquals(ivCipherText, it)
            expectedDecryptedBytes
        }

        val result = ivCipherText.decrypt(decrypter)

        Assertions.assertEquals("decrypted", result)
    }
}