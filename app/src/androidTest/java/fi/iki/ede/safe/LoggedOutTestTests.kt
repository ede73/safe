package fi.iki.ede.safe

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen.Companion.CATEGORY_ID
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.utilities.LoggedOutTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// test that main interfaces support automatic transition to login screen
// if for what ever reason (backbuffer) launched logged out (auto logout)
// Alas there isn't easy way to make these dynamic due to @get:Rule)
@RunWith(AndroidJUnit4::class)
class SiteEntryListScreenStartLoggedOutTest : LoggedOutTest() {
    // TODO: what a work to pass Intent to activity! write a wrapper!
    @get:Rule
    val composeTest = createEmptyComposeRule()
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    lateinit var scenario: ActivityScenario<SiteEntryListScreen>

    @Before
    override fun beforeEachTest() {
        super.beforeEachTest()
        scenario = launch(Intent(context, SiteEntryListScreen::class.java).apply {
            putExtra(CATEGORY_ID, 1L)
        })
    }

    @Test
    fun test() {
        composeTest.onAllNodesWithTag(TestTag.PASSWORD_PROMPT)[0]
            .assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class CategoryListScreenStartLoggedOutTest : LoggedOutTest() {
    @get:Rule
    val composeTest = createAndroidComposeRule<CategoryListScreen>()

    @Test
    fun test() =
        transitionToLoginScreenIfNotLoggedIn(composeTest)
}

@RunWith(AndroidJUnit4::class)
class SiteEntryEditScreenStartLoggedOutTest : LoggedOutTest() {
    @get:Rule
    val composeTest = createAndroidComposeRule<SiteEntryEditScreen>()

    @Test
    fun test() =
        transitionToLoginScreenIfNotLoggedIn(composeTest)
}
