package fi.iki.ede.safe.login

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.utilities.AutoMockingUtilities
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsInitialized
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsFirstTimeLogin
import fi.iki.ede.safe.utilities.LoginScreenHelper
import fi.iki.ede.safe.utilities.MockDataModel
import fi.iki.ede.safe.utilities.MyResultLauncher
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test scenario when app is fresh installed, all defaults apply
 * No database, no password, no biometrics
 *
 * Test plan, verify that...
 * - must login, initialize keystore, not launch biometrics registration (since not enabled) [newLoginAndBioLaunchedWhenUnchecked]
 * - must login, initialize keystore, launch biometrics registration (since enabled) [newLoginAndBioLaunchedWhenChecked]
 */
@Ignore
@RunWith(AndroidJUnit4::class)
class DeepLoginFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {
    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    @After
    fun clearAll() {
        MyResultLauncher.afterEachTest()
    }

    @Test
    fun newLoginAndBioLaunchedWhenUnchecked() {
        // global flag, but also first time init biometrics enable/disable
        mockIsBiometricsEnabled { false }
        mockIsBiometricsInitialized { false }
        every { BiometricsActivity.getRegistrationIntent(any()) } answers { Intent() }
        every { BiometricsActivity.getVerificationIntent(any()) } answers { Intent() }

        // TODO: Logic is "iffy", we've mocked mockIsBiometricsEnabled
        // but if we DO enable the biometrics checkbox, it tries to
        // call BiometricsActivity.setBiometricEnabled(true) and messes up the results
        mockkObject(IntentManager)
        properPasswordLogin(false)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 0) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 1) { IntentManager.startCategoryScreen(any()) }
    }

    @Test
    fun newLoginAndBioLaunchedWhenChecked() {
        mockIsBiometricsEnabled { true }
        mockIsBiometricsInitialized { false }
//        val expectedIntent = Intent(
//            InstrumentationRegistry.getInstrumentation().targetContext,
//            BiometricsActivity::class.java
//        )
//        every { BiometricsActivity.getRegistrationIntent(any()) } returns expectedIntent

        MyResultLauncher.registerTestLaunchResult(TestTag.TEST_TAG_LOGIN_BIOMETRICS_REGISTER) {
            println("Cancelling biometrics")
            ActivityResult(RESULT_CANCELED, null)
        }
        // login and pop biometric prompt (the mock intent)
        properPasswordLogin(true)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getVerificationIntent(any()) }
        verify(exactly = 1) { BiometricsActivity.getRegistrationIntent(any()) }

        MyResultLauncher.fetchResults()

        val la =
            MyResultLauncher.getLaunchedIntentsAndCallback(TestTag.TEST_TAG_LOGIN_BIOMETRICS_REGISTER)
        assert(la.second.size == 1) {
            "Only one intent expected, got ${la.second.size}"
        }
        // Verify biometrics indeed was launched
        assert(la.second[0].component?.className == BiometricsActivity::class.qualifiedName) {
            "${la.second[0].component?.className} != ${BiometricsActivity::class.qualifiedName}"
        }
    }

    // TODO: add also test case for failed biometrics
    // TODO: cancelled biometrics

    private fun properPasswordLogin(biometricsRegister: Boolean) {
        every { LoginHandler.passwordLogin(any(), any()) } returns true
        // TODO: this should INITIALIZE keystore
        every { LoginHandler.firstTimeLogin(any(), any()) } just runs

        mockkObject(IntentManager)
        every { IntentManager.startCategoryScreen(any()) } just runs

        // for the FIRST TIME init we do display biometrics
        getPasswordFields(loginActivityTestRule)[0].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[1].assertIsDisplayed()

        //    if (BiometricsActivity.isBiometricEnabled() &&
        //    BiometricsActivity.haveRecordedBiometric() &&
        //    keystoreIsInitialized) { -> button else checkbox
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()

        val biometricsChecked =
            getBiometricsCheckbox(loginActivityTestRule).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

        if (biometricsRegister) {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.Off) {
                println("Turn on biometrics checkbox")
                getBiometricsCheckbox(loginActivityTestRule).performClick()
                getBiometricsCheckbox(loginActivityTestRule).assertIsChecked()
            }
        } else {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.On) {
                println("Turn off biometrics checkbox")
                getBiometricsCheckbox(loginActivityTestRule).performClick()
                getBiometricsCheckbox(loginActivityTestRule).assertIsNotChecked()
            }
        }
        getPasswordFields(loginActivityTestRule)[0].performTextInput("quite_a_password")
        getPasswordFields(loginActivityTestRule)[1].performTextInput("quite_a_password")
        getLoginButton(loginActivityTestRule).performClick()

        verify(exactly = 1) { LoginHandler.firstTimeLogin(any(), any()) }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            if (InstrumentationRegistry.getArguments().getString("test") == "true")
                System.setProperty("test", "true")

            mockkObject(Preferences)
            mockkObject(BiometricsActivity)
            mockIsFirstTimeLogin { true }
            MyResultLauncher.beforeClassJvmStaticSetup()

            // initializes keystore, we don't want that!
            // missing keystore is the state of the 'first install
            MockDataModel.mockAllDataModelNecessities()

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns false
        }

        @AfterClass
        @JvmStatic
        fun deInitialize() {
            unmockkAll()
        }
    }
}