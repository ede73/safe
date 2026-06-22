package fi.iki.ede.crypto

import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.IKeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot
import kotlin.experimental.xor

object KeystoreHelperMock4UnitTests {
    fun mock() {
        mockkObject(KeyStoreHelperFactory)
        val mockKeyStoreHelper = mockkClass(IKeyStoreHelper::class)
        KeyStoreHelperFactory.provideKeyStoreHelper = mockKeyStoreHelper
        every { KeyStoreHelperFactory.getKeyStoreHelper } returns { mockKeyStoreHelper }
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

        every { mockKeyStoreHelper.encrypterProvider } answers {
            { plaintext -> fakeEncrypt(plaintext) }
        }

        every { mockKeyStoreHelper.encrypterProviderWithKey } answers {
            { plaintext, _ -> fakeEncrypt(plaintext) }
        }

        every { mockKeyStoreHelper.decrypterProvider } answers {
            { encrypted -> fakeDecrypt(encrypted) }
        }
        every { mockKeyStoreHelper.decrypterProviderWithKey } answers {
            { encrypted, _ -> fakeDecrypt(encrypted) }
        }
    }
}
