package fi.iki.ede.crypto.testutils

import fi.iki.ede.crypto.IVCipherText
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
        val p = mockkClass(IKeyStoreHelper::class)
        KeyStoreHelperFactory.provideKeyStoreHelper = p
        every { KeyStoreHelperFactory.getKeyStoreHelper } returns { p }
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



        every { p.encrypterProvider } answers {
            { plaintext -> fakeEncrypt(plaintext) }
        }

        every { p.encrypterProviderWithKey } answers {
            { plaintext, _ -> fakeEncrypt(plaintext) }
        }

        every { p.decrypterProvider } answers {
            { encrypted -> fakeDecrypt(encrypted) }
        }
        every { p.decrypterProviderWithKey } answers {
            { encrypted, _ -> fakeDecrypt(encrypted) }
        }
    }
}
