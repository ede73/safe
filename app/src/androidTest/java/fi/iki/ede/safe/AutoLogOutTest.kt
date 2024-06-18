package fi.iki.ede.safe

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.service.AutolockingService
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.utilities.AutoMockingUtilities.Companion.mockIsBiometricsEnabled
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.MockKeyStore
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class AutoLogOutTest {
//    @get:Rule(order = 0)
//    val serviceRule: ServiceTestRule = ServiceTestRule.withTimeout(30, TimeUnit.SECONDS)

    @get:Rule
    val activityTestRule = createAndroidComposeRule<CategoryListScreen>()
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAutoLogoutService() = runTest {
        println("=========assert displays category ")
        activityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].assertIsDisplayed()

        val serviceIntent = Intent(
            getApplicationContext(),
            AutolockingService::class.java
        )

        InstrumentationRegistry.getInstrumentation().targetContext.startService(serviceIntent)
        println("========= service started")
        advanceUntilIdle()

        // Alas, you can advance time, but it won't affect outside running CountdownTimer
        // TODO: SADLY without waits this is flaky test
        // even if timeout it super small, if android emy is busy - IN THIS CODE PATH -
        // there's nothing we can do to make the CounterDown timer firer quicker (milliseconds)
        // unless the service code actually RUNS,trying to lure it does...
        (1..20).forEach { _ ->
            Thread.sleep(100)
            advanceUntilIdle()
        }
        println("========= waited...")
        activityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_PROMPT)[0]
            .assertIsDisplayed()
        println("========= stop service...")
        InstrumentationRegistry.getInstrumentation().targetContext.stopService(serviceIntent)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockKeyStore.mockKeyStore()

            mockkObject(Preferences)
            every { Preferences.getLockTimeoutDuration() } returns 100.milliseconds
            every { Preferences.getLockOnScreenLock(any()) } returns true
            mockkObject(BiometricsActivity)
            mockIsBiometricsEnabled { false }

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