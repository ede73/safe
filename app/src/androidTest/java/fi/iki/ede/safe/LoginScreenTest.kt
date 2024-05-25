package fi.iki.ede.safe

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptablePasswordEntry
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.AutoMockingUtilities.Companion.getBiometricsEnabled
import fi.iki.ede.safe.LoginScreenFirstInstallTest.Companion.mockKeyStoreHelper
import fi.iki.ede.safe.ui.activities.LoginScreen
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test logging in when password has been previously set
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginScreenTest : AutoMockingUtilities, LoginScreenHelper {

    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    companion object {
        private val FAKE_SALT = Salt("abcdabcd01234567".hexToByteArray())
        private const val FAKE_PASSWORD_TEXT = "abcdefgh"
        private val FAKE_PASSWORD = Password(FAKE_PASSWORD_TEXT.toByteArray())
        private val FAKE_MASTERKEY_AES =
            "00112233445566778899AABBCCDDEEFF99887766554433221100123456789ABC".hexToByteArray()
        private val FAKE_ENCRYPTED_MASTERKEY =
            KeyManagement.encryptMasterKey(
                KeyStoreHelper.generatePBKDF2(FAKE_SALT, FAKE_PASSWORD),
                FAKE_MASTERKEY_AES
            )

        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            getBiometricsEnabled(biometrics = { false })
            mockKeyStoreHelper()
            val ks = KeyStoreHelperFactory.getKeyStoreHelper()
            AutoMockingUtilities.fetchDBKeys(
                masterKey = { FAKE_ENCRYPTED_MASTERKEY },
                salt = { FAKE_SALT }, fetchPasswordsOfCategory = {
                    listOf(DecryptablePasswordEntry(1).let {
                        it.id = 1
                        it
                    })
                }, fetchCategories = {
                    listOf(DecryptableCategoryEntry().let {
                        it.id = 1
                        it.encryptedName = ks.encryptByteArray("one".toByteArray())
                        it
                    })
                })

            //            mockkConstructor(LoginScreen::class)
//            every {
//                constructedWith<LoginScreen>().passwordValidated(
//                    any(),
//                    any()
//                )
//            } returns mockk()
        }
    }

    @Test
    fun verifyLoginScreenAfterInitialSetupTest() {
        getPasswordFields(loginActivityTestRule).assertCountEquals(1)
        getPasswordFields(loginActivityTestRule)[0].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[0].assertIsFocused()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        getBiometricsButton(loginActivityTestRule).assertDoesNotExist()
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()
    }

    // Somewhat theoretical, but IF old DB restored and it DID have a short password
    // NOT accepting certain length would prevent user from using the app
    @Test
    fun shortPasswordsMustBeAcceptedTest() {
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
        getPasswordFields(loginActivityTestRule)[0].performTextInput("a")
        getLoginButton(loginActivityTestRule).assertIsEnabled()
    }

    @Test
    fun verifyLoginSucceeds() {
        getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsEnabled()
        getLoginButton(loginActivityTestRule).performClick()
        // Ensure passwordValidated gets called - again CTOR mocking fails
        // multiDexEnabled false lessens errors, but still wont work
        //verify(atLeast = 3) { q.passwordValidated(any(), any()) }
        // It takes time to get here...
        // verify { anyConstructed<LoginScreen>().passwordValidated(any(), any()) }

        // Will call fi.iki.ede.safe.db.DBHelper.fetchAllRows on success
    }
}