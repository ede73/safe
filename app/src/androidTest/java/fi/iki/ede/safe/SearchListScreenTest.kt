package fi.iki.ede.safe

import android.content.Context
import android.app.Instrumentation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.db.DBID
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntrySearchScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest.DEFAULT_2ND_CATEGORY
import fi.iki.ede.safe.utilities.MockKeyStore
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * Test plan, verify that...
 * - finds the passwords by description [trySearch]
 * - finds the passwords by encrypted passwords TODO:
 * - finds the passwords by encrypted notes TODO:
 * - finds the passwords by encrypted website TODO:
 * - finds the passwords by encrypted username TODO:
 * - TODO: test transition to LoginScreen after timeout
 */
@RunWith(AndroidJUnit4::class)
@ExperimentalTime
@ExperimentalFoundationApi
class SearchListScreenTest {
    @get:Rule
    val siteEntryActivityTestRule = createAndroidComposeRule<SiteEntrySearchScreen>()
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.justStoreSaltAndMasterKey(
            initializeMasterKey = MockKeyStore.fakeEncryptedMasterKey,
            initializeSalt = MockKeyStore.fakeSalt,
        )
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun trySearch() = runTest {
        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .assertIsDisplayed()
        // DEFAULT_2ND_CATEGORY -> category name is part of default passwords
        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .performTextInput(DEFAULT_2ND_CATEGORY)
        siteEntryActivityTestRule.waitForIdle()

        advanceUntilIdle()

        siteEntryActivityTestRule.waitUntil(timeoutMillis = 5000) {
            siteEntryActivityTestRule.onAllNodesWithTag(
                TestTag.SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 2
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun findsEntryArrivingAfterQueryWithoutFurtherTyping() = runTest {
        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .performTextInput("late-arrival-unique-query")
        siteEntryActivityTestRule.waitForIdle()
        Thread.sleep(750L) // Let the initial debounce/search finish before adding the row.

        val categoryId = DataModel.categoriesStateFlow.value.first().id!!
        val addedEntry = DecryptableSiteEntry(categoryId).apply {
            description = "late-arrival-unique-query-entry".encrypt()
        }
        val insertedId = CompletableDeferred<DBID>()
        DataModel.addOrUpdateSiteEntry(addedEntry) { entry ->
            insertedId.complete(entry.id!!)
        }
        insertedId.await()

        siteEntryActivityTestRule.waitUntil(timeoutMillis = 10_000L) {
            siteEntryActivityTestRule.onAllNodesWithTag(
                TestTag.SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 1
        }
        siteEntryActivityTestRule.onAllNodesWithTag(
            TestTag.SEARCH_MATCH,
            useUnmergedTree = true
        )[0].assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clearingSearchStopsThePreviousQueryCollector() = runTest {
        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .performTextInput("cleared-query-unique")
        siteEntryActivityTestRule.waitForIdle()
        Thread.sleep(750L)

        siteEntryActivityTestRule.onNodeWithTag(
            TestTag.SEARCH_CLEAR_BUTTON,
            useUnmergedTree = true
        )
            .performTouchInput { click() }
        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .assertTextContains("")

        val categoryId = DataModel.categoriesStateFlow.value.first().id!!
        val addedEntry = DecryptableSiteEntry(categoryId).apply {
            description = "cleared-query-unique-late-entry".encrypt()
        }
        val insertedId = CompletableDeferred<DBID>()
        DataModel.addOrUpdateSiteEntry(addedEntry) { entry ->
            insertedId.complete(entry.id!!)
        }
        insertedId.await()
        Thread.sleep(750L)

        assertEquals(
            0,
            siteEntryActivityTestRule.onAllNodesWithTag(
                TestTag.SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updatedSearchResultKeepsItsIdAndCanOpen() = runTest {
        val categoryId = DataModel.categoriesStateFlow.value.first().id!!
        val original = DecryptableSiteEntry(categoryId).apply {
            description = "same-id-openable-entry".encrypt()
        }
        val insertedId = CompletableDeferred<DBID>()
        DataModel.addOrUpdateSiteEntry(original) { entry ->
            insertedId.complete(entry.id!!)
        }
        val id = insertedId.await()

        siteEntryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .performTextInput("same-id-openable")
        siteEntryActivityTestRule.waitUntil(timeoutMillis = 10_000L) {
            siteEntryActivityTestRule.onAllNodesWithTag(
                TestTag.SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 1
        }

        val stored = DataModel.getSiteEntry(id)
        val updatedDescription = "same-id-openable-entry-updated"
        val updated = DecryptableSiteEntry(stored.categoryId).apply {
            this.id = stored.id
            description = updatedDescription.encrypt()
            website = stored.website
            username = stored.username
            password = stored.password
            note = stored.note
            extensions = stored.extensions
            passwordChangedDate = stored.passwordChangedDate
            photoFilename = stored.photoFilename
            photo = stored.photo
        }
        DataModel.addOrUpdateSiteEntry(updated)
        siteEntryActivityTestRule.waitUntil(timeoutMillis = 5_000L) {
            DataModel.siteEntriesStateFlow.value.any {
                it.id == id && it.description === updated.description
            }
        }
        siteEntryActivityTestRule.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                siteEntryActivityTestRule.onAllNodesWithTag(
                    TestTag.SEARCH_MATCH,
                    useUnmergedTree = true
                )[0].assertTextContains(updatedDescription)
            }.isSuccess
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            SiteEntryEditScreen::class.java.name,
            null,
            false
        )
        siteEntryActivityTestRule.onAllNodesWithTag(
            TestTag.SEARCH_MATCH,
            useUnmergedTree = true
        )[0].performTouchInput { click() }
        val editActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000L)
        instrumentation.removeMonitor(monitor)

        assertNotNull("Updated search result did not open the editor", editActivity)
        val openedActivity = requireNotNull(editActivity)
        assertEquals(id, openedActivity.intent.getLongExtra(SiteEntryEditScreen.SITE_ENTRY_ID, -1L))
        openedActivity.runOnUiThread { openedActivity.finish() }
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
