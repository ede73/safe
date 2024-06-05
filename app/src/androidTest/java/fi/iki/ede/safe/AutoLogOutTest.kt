package fi.iki.ede.safe

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
import fi.iki.ede.safe.utilities.MockDBHelper
import fi.iki.ede.safe.utilities.MockDataModel
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
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class AutoLogOutTest {
//    @get:Rule(order = 0)
//    val serviceRule: ServiceTestRule = ServiceTestRule.withTimeout(30, TimeUnit.SECONDS)

    @get:Rule
    val activityTestRule = createAndroidComposeRule<CategoryListScreen>()

    @Before
    fun beforeEachTest() {
        MockDBHelper.initializeBasicTestDataModel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAutoLogoutService() = runTest {
        val serviceIntent = Intent(
            getApplicationContext(),
            AutolockingService::class.java
        )

        InstrumentationRegistry.getInstrumentation().targetContext.startService(serviceIntent)

        advanceUntilIdle()

        activityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].assertIsDisplayed()
        advanceUntilIdle()
        // Alas, you can advance time, but it won't affect outside running CountdownTimer
        Thread.sleep(1000)
        advanceUntilIdle()
        activityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_PASSWORD_PROMPT)[0]
            .assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().targetContext.stopService(serviceIntent)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockDataModel.mockAllDataModelNecessities()

            mockkObject(Preferences)
            every { Preferences.getLockTimeoutDuration() } returns 1.seconds
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