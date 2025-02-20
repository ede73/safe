package fi.iki.ede.safe.login

import android.app.Activity.RESULT_CANCELED
import android.content.Context
import android.util.Log
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
import fi.iki.ede.backup.MyBackupAgent
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.utilities.AutoMockingUtilities
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsInitialized
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.LoginScreenHelper
import fi.iki.ede.safe.utilities.MockKeyStore
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import fi.iki.ede.safe.utilities.MyResultLauncher
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "LoginScreenAfterFirstInstallTest"

/**
 * Test scenario when app user has already logged in once
 *
 * Test plan, verify that...
 * - we have password/biometrics displayed [verifyLoginScreenAfterInitialSetupTest]
 * - despite pwd min req, user must be able to log in with short password [shortPasswordsMustBeAcceptedTest]
 * - password login works (biometrics already initialized, but not enabled) [testLoggingInWorks]
 * - biometrics available, must open biometric prompt [testLoggingWorksWithBiometrics]
 * - biometrics available, must open biometric prompt, but we cancel it and login with password [testLoggingWorksWithCancelledBiometrics]
 * - biometrics available, must open biometric prompt, but biometrics reading fails, we login with passeord [testLoggingWorksWithFailingBiometrics]
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenAfterFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {
    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.justStoreSaltAndMasterKey(
            initializeMasterKey = fakeEncryptedMasterKey,
            initializeSalt = fakeSalt,
        )
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()
    }

    @After
    fun clearAll() {
        MyResultLauncher.afterEachTest()
    }

    @Test
    fun verifyLoginScreenAfterInitialSetupTest() {
        // probably too late to change...
        mockIsBiometricsInitialized { true }
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
        mockkObject(IntentManager)
        getPasswordFields(loginActivityTestRule)[0].performTextInput(MockKeyStore.FAKE_PASSWORD_PLAINTEXT)
        getLoginButton(loginActivityTestRule).assertIsEnabled()
        getLoginButton(loginActivityTestRule).performClick()
        verify(exactly = 1) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 1) { IntentManager.startCategoryScreen(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }
    }

    @Test
    @Ignore
    fun testLoggingWorksWithBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.LOGIN_BIOMETRICS_VERIFY) {
            Log.d(TAG, "Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }

        //mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 1) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}: " + (la.second.joinToString(separator = ",") { it.toString() })
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }
    }

    @Test
    @Ignore
    fun testLoggingWorksWithCancelledBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.LOGIN_BIOMETRICS_VERIFY) {
            Log.d(TAG, "Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }

        //mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 1) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}: " + (la.second.joinToString(separator = ",") { it.toString() })
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
    @Ignore
    fun testLoggingWorksWithFailingBiometrics() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { true }

        MyResultLauncher.registerTestLaunchResult(TestTag.LOGIN_BIOMETRICS_VERIFY) {
            Log.d(TAG, "Cancelling biometrics")
            ActivityResult(BiometricsActivity.RESULT_FAILED, null)
        }

        //mockkObject(CategoryListScreen)
        //getPasswordFields(loginActivityTestRule)[0].performTextInput(FAKE_PASSWORD_TEXT)
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        //getBiometricsCheckbox(loginActivityTestRule).performClick()
        //getLoginButton(loginActivityTestRule).performClick()

        //verify(exactly = 1) { CategoryListScreen.startMe(any()) }
        verify(exactly = 0) { LoginHandler.firstTimeLogin(any()) }
        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 1) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 0) { LoginHandler.biometricLogin() }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.LOGIN_BIOMETRICS_VERIFY)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}: " + (la.second.joinToString(separator = ",") { it.toString() })
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

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            if (InstrumentationRegistry.getArguments().getString("test") == "true")
                System.setProperty("test", "true")

            mockkObject(Preferences)
            mockkObject(BiometricsActivity)
            MyResultLauncher.beforeClassJvmStaticSetup()

            MockKeyStore.mockKeyStore()

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns true
            MyBackupAgent.removeRestoreMark(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
            val context =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            MyBackupAgent.removeRestoreMark(context)
            // we'll overwrite the DBHelper with in-memory one...
            DBHelperFactory.initializeDatabase(
                DBHelper(
                    context, null, false, GPMDB::getExternalTables,
                    GPMDB::upgradeTables
                )
            )
            DBHelper4AndroidTest.justStoreSaltAndMasterKey(
                initializeMasterKey = fakeEncryptedMasterKey,
                initializeSalt = fakeSalt,
            )
            DBHelper4AndroidTest.initializeEverything(context)

        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}