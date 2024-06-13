package fi.iki.ede.safe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import fi.iki.ede.safe.utilities.MockDBHelper
import fi.iki.ede.safe.utilities.MockDataModel
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

    val testDispatcher = StandardTestDispatcher()

    @Before
    fun beforeEachTest() {
        MockDBHelper.initializeBasicTestDataModel()
    }

    @Test
    fun verifyBothCategoriesShow() {
        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].assertIsDisplayed()
        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[1].assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addCategory() = runTest(testDispatcher, timeout = 10.seconds) {
        val newCategory = "newCategory"
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_TOP_ACTION_BAR_ADD)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_BUTTON)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_TEXT_FIELD)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_TEXT_FIELD)
            .assertIsFocused()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_TEXT_FIELD)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_TEXT_FIELD)
            .performTextInput(newCategory)
        advanceUntilIdle()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_BUTTON)
            .performClick()

        advanceUntilIdle()
        assert(
            categoryActivityTestRule.onAllNodesWithTag(
                TestTag.TEST_TAG_CATEGORY_ROW,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 3
        )
        val categoriesEmitted = DataModel.categoriesStateFlow.value
        val wasCategoryAdded = categoriesEmitted.any { it.plainName == newCategory }
        assert(MockDBHelper.categories.find { it.plainName == newCategory } != null)
        { "Failed to find added category from the DB" }

        assertTrue("The category was not added as expected.", wasCategoryAdded)
    }

    @Test
    fun tryOpenSearchSearch() {
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_TOP_ACTION_BAR_SEARCH)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .assertIsDisplayed()
        // DEFAULT_2ND_CATEGORY -> category name is part of the default site entry description
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .performTextInput(MockDBHelper.DEFAULT_2ND_CATEGORY)
        categoryActivityTestRule.waitForIdle()
        categoryActivityTestRule.waitForIdle()
        categoryActivityTestRule.waitUntil(timeoutMillis = 5000) {
            categoryActivityTestRule.onAllNodesWithTag(
                TestTag.TEST_TAG_SEARCH_MATCH,
                useUnmergedTree = true
            ).fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun mustNotBeAbleToDeleteNonEmptyCategories() {
        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].performTouchInput {
            longClick()
        }
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_EDIT)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
            .assertIsNotDisplayed()
        assert(MockDBHelper.categories.find { it.plainName == MockDBHelper.DEFAULT_1ST_CATEGORY } != null)
        { "Failed to find cancelled deleted category from the DB" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteCategoryCancel() {
        val newCategory = "newCategory"
        runTest {
            MockDBHelper.addCategory(newCategory)
            runBlocking {
                DataModel.loadFromDatabase()
            }

            val categoriesEmitted = mutableListOf<List<DecryptableCategoryEntry>>()
            val collectionJob = launch {
                DataModel.categoriesStateFlow.collectLatest { categories ->
                    categoriesEmitted.add(categories)
                }
            }

            categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].performTouchInput {
                longClick()
            }
            categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
                .assertIsDisplayed()
            categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
                .performClick()
            categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CANCEL)
                .assertIsDisplayed()
            categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CANCEL)
                .performClick()
            advanceUntilIdle()
            categoryActivityTestRule.waitUntil(10000) {
                // wait until one category disappears
                categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)
                    .fetchSemanticsNodes().size == 3
            }
            val categoryStillExists = categoriesEmitted.any { categoriesList ->
                categoriesList.any { it.plainName == newCategory }
            }
            assertTrue("The category was deleted.", categoryStillExists)
            collectionJob.cancel()
            assert(MockDBHelper.categories.find { it.plainName == newCategory } != null)
            { "Failed to find cancelled deletion-cancelled category from the DB" }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteCategory() = runTest {
        val newCategory = "newCategory"
        MockDBHelper.addCategory(newCategory)
        runBlocking {
            DataModel.loadFromDatabase()
        }

        val categoriesEmitted = mutableListOf<List<DecryptableCategoryEntry>>()
        val collectionJob = launch {
            DataModel.categoriesStateFlow.collectLatest { categories ->
                categoriesEmitted.add(categories)
            }
        }

        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].performTouchInput {
            longClick()
        }
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CONFIRM)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE_CONFIRM)
            .performClick()
        categoryActivityTestRule.waitForIdle()
        advanceUntilIdle()

        categoryActivityTestRule.waitUntil(5000) {
            advanceUntilIdle()
            // wait until one category disappears
            categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)
                .fetchSemanticsNodes().size == 2
        }
        advanceUntilIdle()
        val categoryStillExists = categoriesEmitted.any { categoriesList ->
            categoriesList.any { it.plainName == newCategory }
        }
        assertFalse("The category was deleted.", categoryStillExists)
        collectionJob.cancel()

        assert(MockDBHelper.categories.find { it.plainName == newCategory } == null)
        { "Found deleted category from the DB" }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun initialize() {
            MockDataModel.mockAllDataModelNecessities()

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
