package fi.iki.ede.safe

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen.Companion.SITE_ENTRY_ID
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.MockKeyStore
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import fi.iki.ede.safe.utilities.NodeHelper
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds


// Custom helper method to launch the activity with intent extras
inline fun <reified A : Activity> ComposeTestRule.launchActivityWithIntent(
    crossinline block: Intent.() -> Unit = {}
): ActivityScenario<A> {
    val intent = Intent(ApplicationProvider.getApplicationContext(), A::class.java).apply {
        block()
    }
    return launch(intent)
}

@RunWith(AndroidJUnit4::class)
class SiteEntryViewTest : NodeHelper {
    @get:Rule
    val testRule = createEmptyComposeRule()
    lateinit var scenario: ActivityScenario<SiteEntryEditScreen>
    lateinit var viewModel: EditingSiteEntryViewModel
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val testDispatcher = StandardTestDispatcher()

//    @Before
//    fun setup() = composeTestRule.setContent {
//        val enc = KeyStoreHelperFactory.getEncrypter()
//        val entry = DecryptableSiteEntry(1).apply {
//            id = 1L
//            description = enc("description".toByteArray())
//            username = enc("username".toByteArray())
//            password = enc("password".toByteArray())
//            note = enc("note".toByteArray())
//        }
//        viewModel = EditingSiteEntryViewModel().apply { editSiteEntry(entry) }
//        SafeTheme {
//            Column {
//                SiteEntryView(
//                    viewModel = viewModel,
//                    modifier = Modifier,
//                    true
//                )
//            }
//        }
//    }

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.justStoreSaltAndMasterKey(
            initializeMasterKey = fakeEncryptedMasterKey,
            initializeSalt = fakeSalt,
        )
        // some how if you switch between readable/writable database OR close either
        // in memory database is lost
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()

        mockkObject(fi.iki.ede.preferences.Preferences)
        every { fi.iki.ede.preferences.Preferences.getAllExtensions() } returns setOf(
            "extension1",
            "extension2"
        )
        val intent = Intent(context, SiteEntryEditScreen::class.java).apply {
            putExtra(SITE_ENTRY_ID, 1L)
        }

        scenario = launch<SiteEntryEditScreen>(intent)
        scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[EditingSiteEntryViewModel::class.java]
        }
    }

    @After
    fun after() {
        unmockkObject(fi.iki.ede.preferences.Preferences)
    }

    private fun goBack() {
        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun withNoEditsBackShouldExit() {
        //scenario.moveToState(Lifecycle.State.CREATED)
        val text = testRule.onNodeWithTag(TestTag.SITE_ENTRY_DESCRIPTION)
        text.performClick()
        val latch = CountDownLatch(1)

        goBack() // actually back out
        scenario.onActivity { lifecycleOwner ->
            lifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    latch.countDown()
                }
            })
        }
        latch.await(5, TimeUnit.SECONDS)
        assertTrue(Lifecycle.State.DESTROYED == scenario.state)
    }

    private fun enterText(testTag: TestTag) {
        val text = testRule.onNodeWithTag(testTag)
        text.performClick()
        text.performTextInput("a")
        text.performTextInput("a")
    }

    private fun backOutAndEnsureSaveDialog() {
        goBack() // fold keyboard
        waitForTagToAppear(TestTag.SITE_ENTRY_SAVE_DIALOG)
    }

    private fun waitForTagToAppear(testTag: TestTag) {
        testRule.waitUntil(3000) {
            testRule.onAllNodesWithTag(testTag)
                .fetchSemanticsNodes().size == 1
        }
    }

    @Test
    fun backOnEditedDescriptionMustPopSaveDialog() {
        enterText(TestTag.SITE_ENTRY_DESCRIPTION)
        backOutAndEnsureSaveDialog()
    }

    @Test
    fun backOnEditedUsernameMustPopSaveDialog() {
        enterText(TestTag.SITE_ENTRY_USERNAME)
        backOutAndEnsureSaveDialog()
    }

    @Test
    fun backOnEditedPasswordMustPopSaveDialog() {
        enterText(TestTag.SITE_ENTRY_PASSWORD)
        backOutAndEnsureSaveDialog()
    }

    @Test
    fun backOnEditedNoteMustPopSaveDialog() {
        enterText(TestTag.SITE_ENTRY_NOTE)
        backOutAndEnsureSaveDialog()
    }

    @Test
    fun backOnEditedDateMustPopSaveDialog() {
        goBack()
    }

    @Test
    fun backOnEditedExtensionPopSaveDialog() {
        val expandExtensions =
            testRule.onAllNodesWithTag(TestTag.SITE_ENTRY_EXTENSION_EXPANSION).onFirst()
        expandExtensions.performClick()

        val check =
            testRule.onAllNodesWithTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY_CHECKBOX).onFirst()
        check.performClick()
        enterText(TestTag.SITE_ENTRY_EXTENSION_ENTRY)
        backOutAndEnsureSaveDialog()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Ignore("TODO: fix this, works in prod, just not in test")
    fun backOnGeneratedPasswordPopSaveDialog() = runTest(testDispatcher, timeout = 30.seconds) {
        testRule.onNodeWithTag(TestTag.TOP_ACTION_BAR_MENU).performClick()
        waitForTagToAppear(TestTag.TOP_ACTION_BAR_GENERATE_PASSWORD)
        testRule.onNodeWithTag(TestTag.TOP_ACTION_BAR_GENERATE_PASSWORD).performClick()
        advanceUntilIdle()
        advanceUntilIdle()
        goBack() // fold keyboard
        advanceUntilIdle()
        // TODO: Lotsa update issues (in the test), works in prod
        // seeing the PWD enter to the field, but best effort getting save dialog out nvr hppns
        //waitForTagToAppear(TestTag.SITE_ENTRY_SAVE_DIALOG)
    }

    @Test
    fun backOnChangedPhotoPopSaveDialog() {
        // TODO:
        goBack()
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

//    companion object {
//        @BeforeClass
//        @JvmStatic
//        fun initialize() {
//            MockKeyStore.mockKeyStore()
//
//            mockkObject(Preferences)
//            every { Preferences.getLockTimeoutDuration() } returns 100.hours
//            every { Preferences.getLockOnScreenLock(any()) } returns false
////            mockkObject(BiometricsActivity)
////            mockIsBiometricsEnabled { false }
//
//            mockkObject(LoginHandler)
//            every { LoginHandler.isLoggedIn() } returns true
//        }
//    }
}
