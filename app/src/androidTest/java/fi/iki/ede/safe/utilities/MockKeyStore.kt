package fi.iki.ede.safe.utilities

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.hexToByteArray
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot

object MockKeyStore {
    fun isInitialized() = KeyStoreHelperFactory.getKeyStoreHelper().isMock

    fun mockKeyStore(): KeyStoreHelper {
        require(!KeyStoreHelper::class.isMock) { "You should not have mockkObject(KeyStoreHelper) - stern warning" }
        val p = mockkClass(KeyStoreHelper::class)

        mockkObject(KeyStoreHelperFactory)
        //require(KeyStoreHelperFactory.isMock) { "You MUST have mockkObject(KeyStoreHelperFactory)" }
        every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p

        val encryptionInput = slot<ByteArray>()
        every { p.encryptByteArray(capture(encryptionInput)) } answers {
            IVCipherText(ByteArray(CipherUtilities.IV_LENGTH), encryptionInput.captured)
        }

        val decryptionInput = slot<IVCipherText>()
        every { p.decryptByteArray(capture(decryptionInput)) } answers {
            decryptionInput.captured.cipherText
        }
        assert(p == KeyStoreHelperFactory.getKeyStoreHelper()) {
            "Keystore initialization failed"
        }
        return p
    }

    val fakeSalt = Salt("abcdabcd01234567".hexToByteArray())
    const val FAKE_PASSWORD_PLAINTEXT = "abcdefgh"
    val fakePassword = Password(FAKE_PASSWORD_PLAINTEXT)
    private val fakeMasterKeyAES =
        "00112233445566778899AABBCCDDEEFF99887766554433221100123456789ABC".hexToByteArray()
    private val fakePasswordBasedAESKey = generatePBKDF2AESKey(
        fakeSalt,
        KEY_ITERATION_COUNT,
        fakePassword,
        KEY_LENGTH_BITS
    )
    val fakeEncryptedMasterKey = KeyManagement.encryptMasterKey(
        fakePasswordBasedAESKey,
        fakeMasterKeyAES
    )
}