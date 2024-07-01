package fi.iki.ede.safe

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.utilities.DBHelper4AndroidTest
import fi.iki.ede.safe.utilities.MockKeyStore
import fi.iki.ede.safe.utilities.MockKeyStore.fakeEncryptedMasterKey
import fi.iki.ede.safe.utilities.MockKeyStore.fakeSalt
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Test plan, verify that...
 * - correct categories are shown when loaded [verifyBothCategoriesShow]
 * - adding a category works [addCategory]
 * - search can be opened and is functional [tryOpenSearchSearch]
 * - non empty categories` cannot be deleted [mustNotBeAbleToDeleteNonEmptyCategories]
 * - long pressing to delete a category (but cancelling) works [deleteCategoryCancel]
 * - long pressing to delete a category works [deleteCategory]
 * - TODO: test transition to LoginScreen after timeout
 */
@RunWith(AndroidJUnit4::class)
class CategoryListScreenTest {
    @get:Rule
    val categoryActivityTestRule = createAndroidComposeRule<CategoryListScreen>()

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private var writableDatabase: SQLiteDatabase? = null

    @Before
    fun beforeEachTest() {
        DBHelper4AndroidTest.justStoreSaltAndMasterKey(
            initializeMasterKey = fakeEncryptedMasterKey,
            initializeSalt = fakeSalt,
        )
        //writableDatabase =
        DBHelper4AndroidTest.initializeEverything(context)
        DBHelper4AndroidTest.configureDefaultTestDataModelAndDB()
    }

    @After
    fun deleteDatabase() {
        writableDatabase = null
    }

    @Test
    fun verifyBothCategoriesShow() {
        categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)[0].assertIsDisplayed()
        categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)[1].assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addCategory() = runTest(testDispatcher, timeout = 10.seconds) {
        val newCategory = "newCategory"
        categoryActivityTestRule.onNodeWithTag(TestTag.TOP_ACTION_BAR_ADD)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_BUTTON)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_TEXT_FIELD)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_TEXT_FIELD)
            .assertIsFocused()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_TEXT_FIELD)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_TEXT_FIELD)
            .performTextInput(newCategory)
        advanceUntilIdle()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_BUTTON)
            .performClick()

        advanceUntilIdle()
        assert(
            categoryActivityTestRule.onAllNodesWithTag(
                TestTag.CATEGORY_ROW,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 3
        )
        val categoriesEmitted = DataModel.categoriesStateFlow.value
        val wasCategoryAdded = categoriesEmitted.any { it.plainName == newCategory }
        assert(DataModel.categoriesStateFlow.value.find { it.plainName == newCategory } != null)
        //assert(MockDBHelper.categories.find { it.plainName == newCategory } != null)
        { "Failed to find added category from the DB" }

        assertTrue("The category was not added as expected.", wasCategoryAdded)
    }

    @Test
    fun tryOpenSearchSearch() {
        categoryActivityTestRule.onNodeWithTag(TestTag.TOP_ACTION_BAR_SEARCH)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .assertIsDisplayed()
        // DEFAULT_2ND_CATEGORY -> category name is part of the default site entry description
        categoryActivityTestRule.onNodeWithTag(TestTag.SEARCH_TEXT_FIELD)
            .performTextInput(DBHelper4AndroidTest.DEFAULT_2ND_CATEGORY)
        categoryActivityTestRule.waitForIdle()
        categoryActivityTestRule.waitForIdle()
        categoryActivityTestRule.waitUntil(timeoutMillis = 5000) {
            categoryActivityTestRule.onAllNodesWithTag(
                TestTag.SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun mustNotBeAbleToDeleteNonEmptyCategories() {
        categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)[0].performTouchInput {
            longClick()
        }
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_EDIT)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE)
            .assertIsNotDisplayed()
        assert(DataModel.categoriesStateFlow.value
            .find { it.plainName == DBHelper4AndroidTest.DEFAULT_1ST_CATEGORY } != null)
        { "Failed to find cancelled deleted category from the DB" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteCategoryCancel() {
        val newCategory = "newCategory"
        runTest {
            DBHelper4AndroidTest.addCategory(newCategory)
            runBlocking {
                DataModel.loadFromDatabase()
            }

            val categoriesEmitted = mutableListOf<List<DecryptableCategoryEntry>>()
            val collectionJob = launch {
                DataModel.categoriesStateFlow.collectLatest { categories ->
                    categoriesEmitted.add(categories)
                }
            }

            categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)[0].performTouchInput {
                longClick()
            }
            categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE)
                .assertIsDisplayed()
            categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE)
                .performClick()
            categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE_CANCEL)
                .assertIsDisplayed()
            categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE_CANCEL)
                .performClick()
            advanceUntilIdle()
            categoryActivityTestRule.waitUntil(3000) {
                // wait until one category disappears
                categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)
                    .fetchSemanticsNodes().size == 3
            }
            val categoryStillExists = categoriesEmitted.any { categoriesList ->
                categoriesList.any { it.plainName == newCategory }
            }
            assertTrue("The category was deleted.", categoryStillExists)
            collectionJob.cancel()
            assert(DataModel.categoriesStateFlow.value.find { it.plainName == newCategory } != null)
            //assert(MockDBHelper.categories.find { it.plainName == newCategory } != null)
            { "Failed to find cancelled deletion-cancelled category from the DB" }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteCategory() = runTest(testDispatcher, timeout = 10.seconds) {
        val newCategory = "newCategory"
        DBHelper4AndroidTest.addCategory(newCategory)
        runBlocking {
            DataModel.loadFromDatabase()
        }

        advanceUntilIdle()

        val categoriesEmitted = mutableListOf<List<DecryptableCategoryEntry>>()
        val collectionJob = launch {
            DataModel.categoriesStateFlow.collectLatest { categories ->
                categoriesEmitted.add(categories)
            }
        }

        categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)[0].performTouchInput {
            longClick()
        }
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE_CONFIRM)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.CATEGORY_ROW_DELETE_CONFIRM)
            .performClick()
        categoryActivityTestRule.waitForIdle()
        advanceUntilIdle()

        val categoryStillExists = categoriesEmitted.any { categoriesList ->
            categoriesList.any { it.plainName == newCategory }
        }
        assertFalse("The category was deleted.", categoryStillExists)
        collectionJob.cancel()
        advanceUntilIdle()

        assert(DataModel.categoriesStateFlow.value.find { it.plainName == newCategory } == null)
        { "Found deleted category from the DB" }

        categoryActivityTestRule.waitUntil(3000) {
            advanceUntilIdle()
            // wait until one category disappears
            categoryActivityTestRule.onAllNodesWithTag(TestTag.CATEGORY_ROW)
                .fetchSemanticsNodes().size == 2
        }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockKeyStore.mockKeyStore()

            mockkObject(Preferences)
            every { Preferences.getNotificationPermissionDenied() } returns true

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
