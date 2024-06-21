package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

object KeystoreHelperMock4UnitTests {
    fun mock() {
        mockkObject(KeyStoreHelperFactory)
        val p = mockkClass(KeyStoreHelper::class)
        every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
        val encryptionInput = slot<ByteArray>()
        val fakeIV: ByteArray =
            generateSequence<Byte>(1) { (it + 1).toByte() }
                .take(CipherUtilities.IV_LENGTH).toList().toByteArray()

        fun xorByteArrays(iv: ByteArray, array1: ByteArray): ByteArray {
            val result = ByteArray(array1.size)
            for (i in array1.indices) {
                result[i] = array1[i] xor iv[i % iv.size]
            }
            return result
        }


        fun fakeDecrypt(input: IVCipherText): ByteArray {
            // get rid of IV
            return xorByteArrays(input.iv, input.cipherText)
        }

        fun fakeEncrypt(input: ByteArray): IVCipherText {
            return IVCipherText(fakeIV, xorByteArrays(fakeIV, input))
        }

        val mc = mockkClass(Cipher::class)
        every { mc.init(any<Int>(), any<SecretKeySpec>(), any<IvParameterSpec>()) } answers {
        }
        every { mc.update(any<ByteArray>()) } answers {
            String(firstArg() as ByteArray).reversed().toByteArray()
        }
        every { mc.doFinal() } answers {
            ByteArray(0)
        }
        every { mc.blockSize } answers {
            16
        }
        every { p.getAESCipher() } answers {
            mc
        }

        every { p.encryptByteArray(capture(encryptionInput)) } answers {
            fakeEncrypt(encryptionInput.captured)
        }
        val decryptionInput = slot<IVCipherText>()
        every { p.decryptByteArray(capture(decryptionInput)) } answers {
            fakeDecrypt(decryptionInput.captured)
        }
        every { p.decryptByteArray(capture(decryptionInput), any()) } answers {
            fakeDecrypt(decryptionInput.captured)
        }
    }
}