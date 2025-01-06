package fi.iki.ede.safe.noui


import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.password.ChangeMasterKeyAndPassword
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.MockKeyStore
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakePassword
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// TODO: If nothing else, apply change pwd test..
class KeyStoreHelperTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()
    }

    @Test
    fun changeMasterPassword() {
        val oldPassword: Password = fakePassword
        val newPassword = Password("aaaaaaaa")

        val db = DBHelperFactory.getDBHelper(mockk<Context>())

        val oldOnes = db.fetchSaltAndEncryptedMasterKey()
        assert(oldOnes.first == fakeSalt) { "Wrong salt from the start" }
        assert(oldOnes.second == fakeEncryptedMasterKey) { "Wrong masterkey from the start" }

        ChangeMasterKeyAndPassword.changeMasterPassword(
            oldPassword,
            newPassword
        ) { success ->
            assert(success)
            val newOnes = db.fetchSaltAndEncryptedMasterKey()
            // TODO: Our KeyStore MOCK is rigid static, doesn't generate new keys
            assert(newOnes.first == oldOnes.first) { "Salt didnt set(we are actually using same salt in tests..." }
            assert(newOnes.second != oldOnes.second) { "Masterkey didnt set" }
        }
    }

    @Ignore // broken june/2024
    @Test
    fun testKeyStore() {
        val keyStoreHelper = KeyStoreHelper()
        val biokey = "abba"
        val encryptedBin = keyStoreHelper.encryptByteArray(biokey.toByteArray())
        val decrypted = keyStoreHelper.decryptByteArray(encryptedBin)
        Assert.assertEquals(biokey, String(decrypted))
    }

    @Test
    @Ignore("Not implemented")
    fun testKeyRotation() {
        // Need to implement keyrotation test as well
        // Once the X509 expires, we need to reinitialize everything!
        // Maybe sooner?
        val keyStoreHelper = KeyStoreHelper()
        val biokey = "abba"
        val encrypted =
            keyStoreHelper.encryptByteArray(biokey.toByteArray())

        // Not implemented
        keyStoreHelper.rotateKeys()
        // We've lost the ENCRYPTED KEY at this point for sure
        // Can we have access to that via keystore?
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockKeyStore.mockKeyStore()

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns true
        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}