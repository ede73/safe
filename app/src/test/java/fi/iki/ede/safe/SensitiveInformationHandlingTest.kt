package fi.iki.ede.safe

import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.KeystoreHelperMock4UnitTests
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.SaltedPassword
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.encrypt
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpm.model.SavedGPM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TAG = "SensitiveInformationHandlingTest"

class SensitiveInformationHandlingTest {
    lateinit var ks: KeyStoreHelper
    private val uniqueRarePasswordCharacter = 'ä'
    private val password = uniqueRarePasswordCharacter.toString().repeat(5)

    @BeforeEach
    fun before() {
        KeystoreHelperMock4UnitTests.mock()
        ks = KeyStoreHelperFactory.getKeyStoreHelper()
    }

    @Test
    fun testToStringIsOverridden() {
        listOf(
            IncomingGPM::class, SavedGPM::class,
            Password::class, SaltedPassword::class,
        ).forEach { cls ->
            try {
                // Could not find ANY way to check if class implements overridden string
            } catch (e: NoSuchMethodException) {
                fail("Method toString() does not exist in ${cls.qualifiedName}")
            }
        }
    }

    @Test
    fun savedGpmToString() {
        val s = SavedGPM.makeFromEncryptedStringFields(
            0,
            IVCipherText.getEmpty(),
            IVCipherText.getEmpty(),
            IVCipherText.getEmpty(),
            password.encrypt(),
            IVCipherText.getEmpty(),
            false,
            ""
        )

        val toString = s.toString()
        assertEquals(
            false,
            toString.count { it == uniqueRarePasswordCharacter } != 0,
            "Password exposed")
    }

    @Test
    fun incomingGpmToString() {
        val s = IncomingGPM.makeFromCSVImport(
            "", "", "", password, "",
        )
        val toString = s.toString()
        assertEquals(
            false,
            toString.count { it == uniqueRarePasswordCharacter } != 0,
            "Password exposed")
    }

    @Test
    fun passwordToString() {
        val p = Password(password)
        val toString = p.toString()
        assertEquals(
            false,
            toString.count { it == uniqueRarePasswordCharacter } != 0,
            "Password exposed",
        )
    }

    @Test
    fun saltedPasswordToString() {
        val p = SaltedPassword(Salt.getEmpty(), Password(password))
        val toString = p.toString()
        println(toString)
        assertEquals(
            false,
            toString.count { it == uniqueRarePasswordCharacter } != 0,
            "Password exposed",
        )
    }
}