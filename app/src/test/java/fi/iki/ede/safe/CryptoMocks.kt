package fi.iki.ede.safe

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot

object CryptoMocks {
    // TODO: Make shared with instrumentation tests
    fun mockKeyStoreHelper() {
        mockkObject(KeyStoreHelperFactory)
        val p = mockkClass(KeyStoreHelper::class)
        every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
        val encryptionInput = slot<ByteArray>()
        val fakeIV: ByteArray =
            generateSequence<Byte>(1) { (it + 1).toByte() }
                .take(KeyStoreHelper.IV_LENGTH).toList().toByteArray()

        fun fakeDecrypt(input: IVCipherText): ByteArray {
            // get rid of IV
            val all = input.combineIVAndCipherText()
            return all.reversedArray().copyOfRange(fakeIV.size, all.size)
        }

        fun fakeEncrypt(input: ByteArray): IVCipherText {
            val allinput = (fakeIV + input).reversedArray()
            return IVCipherText(
                allinput.copyOfRange(0, fakeIV.size),
                allinput.copyOfRange(fakeIV.size, allinput.size)
            )
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