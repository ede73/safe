package fi.iki.ede.safe

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Salt
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.AutoMockingUtilities.Companion.fetchDBKeys
import fi.iki.ede.safe.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.LoginScreen
import fi.iki.ede.safe.ui.activities.registerActivityForResults
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test scenario when app is fresh installed, all defaults apply
 * No database, no password, no biometrics
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginScreenFirstInstallTest : AutoMockingUtilities, LoginScreenHelper {

    @After
    fun clearAll() {
        launchedIntents.clear()
    }

    class MyResultLauncher(
        private val testTag: TestTag,
        private val callback: ActivityResultCallback<ActivityResult>
    ) : ActivityResultLauncher<Intent>() {

        init {
            require(launchedIntents.keys.none { it -> it.first == testTag }) {
                "Each and TestTag must be unique, else we can't identify who's launching and what"
            }

            launchedIntents[Pair(testTag, callback)] = mutableListOf()
        }

        override fun launch(i: Intent, options: ActivityOptionsCompat?) {
            (launchedIntents[Pair(testTag, callback)] as MutableList<Intent>).add(i)
            val result = ActivityResult(RESULT_CANCELED, null)
            callback.onActivityResult(result)
        }

        override fun launch(i: Intent) = launch(i, null)

        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()

        override fun unregister() {}

        companion object {
            fun getLaunchedIntentsAndCallback(testTag: TestTag) =
                launchedIntents.keys.first { it -> it.first == testTag }.let {
                    Pair(it.second, launchedIntents[it]!!)
                }

            fun clearLaunchedIntents(testTag: TestTag) =
                launchedIntents.remove(launchedIntents.keys.first { it -> it.first == testTag })
        }
    }

    @get:Rule
    val loginActivityTestRule = createAndroidComposeRule<LoginScreen>()

    @Test
    fun verifyPasswordPromptPresentTest() {
        getPasswordFields(loginActivityTestRule)[0].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[1].assertIsDisplayed()
        getPasswordFields(loginActivityTestRule)[0].assertIsFocused()
        getLoginButton(loginActivityTestRule).assertIsDisplayed()
        getBiometricsButton(loginActivityTestRule).assertDoesNotExist()
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()
    }

    @Test
    fun verifyPasswordLengthRequirementTest() {
        val passwordLength = getMinimumPasswordLength(loginActivityTestRule)
        // going thru whole range is way too slow, just 1, and threshold (+-1)
        for (i in listOf(1, passwordLength - 1, passwordLength, passwordLength + 1)) {
            val shortPassword = "a".repeat(i)

            getPasswordFields(loginActivityTestRule)[0].performTextInput(shortPassword)
            // there's now mismatch, MUST not accept
            getLoginButton(loginActivityTestRule).assertIsNotEnabled()
            getPasswordFields(loginActivityTestRule)[1].performTextInput(shortPassword)
            getLoginButton(loginActivityTestRule).assertIsDisplayed()

            if (shortPassword.length >= passwordLength) {
                getLoginButton(loginActivityTestRule).assertIsEnabled()
            } else {
                getLoginButton(loginActivityTestRule).assertIsNotEnabled()
            }
            getPasswordFields(loginActivityTestRule)[0].performTextClearance()
            getPasswordFields(loginActivityTestRule)[1].performTextClearance()
        }
    }

    @Test
    fun verifyMismatchingPasswordNotAcceptedTest() {
        val passwordLength = getMinimumPasswordLength(loginActivityTestRule)
        val longPassword = "a".repeat(passwordLength)
        getPasswordFields(loginActivityTestRule)[0].performTextInput(longPassword + "1")
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
        getPasswordFields(loginActivityTestRule)[1].performTextInput(longPassword + "2")
        getLoginButton(loginActivityTestRule).assertIsNotEnabled()
    }

    @Test
    fun newLoginAndBioLaunchedWhenUnchecked() {
        every { BiometricsActivity.getRegistrationIntent(any()) } answers { Intent() }

        properPasswordLogin(false)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 0) { BiometricsActivity.getRegistrationIntent(any()) }
        verify(exactly = 1) { CategoryListScreen.startMe(any()) }
    }

    @Test
    fun newLoginAndBioLaunchedWhenChecked() {
        mockIsBiometricsEnabled { true }
        val expectedIntent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BiometricsActivity::class.java
        )
        every { BiometricsActivity.getRegistrationIntent(any()) } returns expectedIntent

        // login and pop biometric prompt (the mock intent)
        properPasswordLogin(true)

        verify(exactly = 0) { LoginHandler.passwordLogin(any(), any()) }
        verify(exactly = 1) { BiometricsActivity.getRegistrationIntent(any()) }

        verify { registerActivityForResults(any(), any(), any(), any()) }

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
        mockkObject(LoginHandler)
        mockkObject(CategoryListScreen)
        every { LoginHandler.passwordLogin(any(), any()) } returns true
        every { LoginHandler.firstTimeLogin(any(), any()) } just runs
        every { CategoryListScreen.startMe(any()) } just runs

        // for the FIRST TIME init we do display biometrics
        getBiometricsCheckbox(loginActivityTestRule).assertIsDisplayed()

        val biometricsChecked =
            getBiometricsCheckbox(loginActivityTestRule).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]

        if (biometricsRegister) {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.Off) {
                getBiometricsCheckbox(loginActivityTestRule).performClick()
                getBiometricsCheckbox(loginActivityTestRule).assertIsChecked()
            }
        } else {
            if (biometricsChecked == androidx.compose.ui.state.ToggleableState.On) {
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
        val launchedIntents: MutableMap<Pair<TestTag, ActivityResultCallback<ActivityResult>>, List<Intent>> =
            mutableMapOf()

        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            mockkStatic(::registerActivityForResults)
            // Only place to mock these is here, when @Test starts, it is too late, activity already initialized
            every {
//            registerActivityForResults<Intent, ActivityResult>(
                registerActivityForResults(any(), any(), any(), any())
            } answers {
                val instance = it.invocation
                val testTag = firstArg<TestTag>()
                val contract = secondArg<ActivityResultContract<Intent, ActivityResult>>()
                val callback = thirdArg<ActivityResultCallback<ActivityResult>>()
                val register =
                    lastArg<(ActivityResultContract<Intent, ActivityResult>, ActivityResultCallback<ActivityResult>) -> ActivityResultLauncher<Intent>>()
                MyResultLauncher(testTag, callback)
            }

            // TODO: MOCK THIS TOO!
            BiometricsActivity.clearBiometricKeys()
            mockIsBiometricsEnabled(biometrics = { false })
            mockKeyStoreHelper()

            val ks = KeyStoreHelperFactory.getKeyStoreHelper()

            fetchDBKeys(
                masterKey = { IVCipherText.getEmpty() },
                salt = { Salt.getEmpty() },
                fetchPasswordsOfCategory = {
                    listOf()
                },
                fetchCategories = {
                    listOf(DecryptableCategoryEntry().let {
                        it.id = 1
                        it.encryptedName = ks.encryptByteArray("one".toByteArray())
                        it
                    })
                },
                isFirstTimeLogin = { true }
            )
        }

        // TODO: Make shared with unit tests
        fun mockKeyStoreHelper() {
            mockkObject(KeyStoreHelperFactory)
            val p = mockkClass(KeyStoreHelper::class)
            every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
            val encryptionInput = slot<ByteArray>()
            every { p.encryptByteArray(capture(encryptionInput)) } answers {
                IVCipherText(ByteArray(CipherUtilities.IV_LENGTH), encryptionInput.captured)
            }
            val decryptionInput = slot<IVCipherText>()
            every { p.decryptByteArray(capture(decryptionInput)) } answers {
                decryptionInput.captured.cipherText
            }
        }
    }
}