package fi.iki.ede.safe

import android.app.Activity.RESULT_CANCELED
import androidx.activity.result.ActivityResult
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.hexToByteArray
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_ITERATION_COUNT
import fi.iki.ede.crypto.keystore.CipherUtilities.Companion.KEY_LENGTH_BITS
import fi.iki.ede.crypto.keystore.KeyManagement
import fi.iki.ede.crypto.keystore.KeyManagement.generatePBKDF2AESKey
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.AutoMockingUtilities.Companion.fetchDBKeys
import fi.iki.ede.safe.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.AutoMockingUtilities.Companion.mockIsBiometricsInitialized
import fi.iki.ede.safe.LoginScreenFirstInstallTest.Companion.mockKeyStoreHelper
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test logging in when password has been previously set
 */
//@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginScreenTest : AutoMockingUtilities, LoginScreenHelper {
    @After
    fun clearAll() {
        MyResultLauncher.afterEachTest()
    }

    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    @Test
    fun verifyLoginScreenAfterInitialSetupTest() {
        // probably too late to change...
        mockIsBiometricsInitialized { false }
        mockIsBiometricsEnabled { false }
        getPasswordFields(loginActivityTestRule).assertCountEquals(1)
        getPasswordFields(loginActivityTestRule)[0].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[0].assertIsFocused()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        // Alas currently biometrics are enabled..
        // TODO: LoginScreen reads these biometrics enabled/what not values ONCE
        // so in SetupClass...after we change them here..nothing helps(FIX)
        //getBiometricsButton(loginActivityTestRule).assertDoesNotExist()
        //getBiometricsButton(loginActivityTestRule).assertIsNotEnabled()
        //getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()
    }

    // Somewhat theoretical, but IF old DB restored and it DID have a short password
    // NOT accepting certain length would prevent user from using the app
    @Test
    fun shortPasswordsMustBeAcceptedTest() {
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
        getPasswordFields(loginActivityTestRule)[0].performTextInput("a")
        getLoginButton(loginActivityTestRule).assertIsEnabled()
        // we won't log in, just test that in UI all is peachy
    }

    @Test
    fun testLoggingInWorks() {
        // too late?bio already launched?
        mockIsBiometricsInitialized { true }
        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsEnabled()
        getLoginButton(loginActivityTestRule).performClick()
        verify(exactly = 1) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any(), any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }
    }

    @Test
    fun testLoggingWorksWithBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY) {
            println("Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }

        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any(), any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 0) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}"
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }
    }

    @Test
    fun testLoggingWorksWithCancelledBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY) {
            println("Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }

        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any(), any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 0) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}"
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }

        // we should be back at login screen
        getPasswordFields(loginActivityTestRule)[0].assertIsEnabled()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        getBiometricsButton(loginActivityTestRule).assertIsEnabled()
    }

    @Test
    fun testLoggingWorksWithFailingBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY) {
            println("Cancelling biometrics")
            ActivityResult(BiometricsActivity.RESULT_FAILED, null)
        }

        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any(), any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 0) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.TEST_TAG_LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}"
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }

        // we should be back at login screen
        getPasswordFields(loginActivityTestRule)[0].assertIsEnabled()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        getBiometricsButton(loginActivityTestRule).assertIsEnabled()
    }

    // TODO: same tests with biometrics, success,cancel,failure

    companion object {
        private val FAKE_SALT = Salt("abcdabcd01234567".hexToByteArray())
        private const val FAKE_PASSWORD_TEXT = "abcdefgh"
        private val FAKE_PASSWORD = Password(FAKE_PASSWORD_TEXT.toByteArray())
        private val FAKE_MASTERKEY_AES =
            "00112233445566778899AABBCCDDEEFF99887766554433221100123456789ABC".hexToByteArray()
        private val FAKE_ENCRYPTED_MASTERKEY =
            KeyManagement.encryptMasterKey(
                generatePBKDF2AESKey(
                    FAKE_SALT,
                    KEY_ITERATION_COUNT,
                    FAKE_PASSWORD,
                    KEY_LENGTH_BITS
                ),
                FAKE_MASTERKEY_AES
            )

        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            if (InstrumentationRegistry.getArguments().getString("test") == "true")
                System.setProperty("test", "true")
            MyResultLauncher.beforeClassJvmStaticSetup()

            // TODO: MOCK THIS TOO!
            BiometricsActivity.clearBiometricKeys()

            // TODO: Wont work outside .. Activity doesn't notices changes..
            mockIsBiometricsEnabled { true }
            mockIsBiometricsInitialized { true }
            mockKeyStoreHelper()

            val ks = KeyStoreHelperFactory.getKeyStoreHelper()

            fetchDBKeys(
                masterKey = { FAKE_ENCRYPTED_MASTERKEY },
                salt = { FAKE_SALT }, fetchPasswordsOfCategory = {
                    listOf(DecryptableSiteEntry(1).let {
                        it.id = 1
                        it
                    })
                }, fetchCategories = {
                    listOf(DecryptableCategoryEntry().let {
                        it.id = 1
                        it.encryptedName = ks.encryptByteArray("one".toByteArray())
                        it
                    })
                },
                isFirstTimeLogin = { false })
        }
    }
}