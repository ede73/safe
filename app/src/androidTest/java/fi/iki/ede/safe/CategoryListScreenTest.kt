package fi.iki.ede.safe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.CipherUtilities
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.onAllNodesWithTag
import fi.iki.ede.safe.ui.onNodeWithTag
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// https://developer.android.com/jetpack/compose/testing
//@LargeTest
@RunWith(AndroidJUnit4::class)
class CategoryListScreenTest {

    @get:Rule
    val categoryActivityTestRule = createAndroidComposeRule<CategoryListScreen>()

    @Before
    fun beforeEachTest() {
        mockDataModel(ks!!)
        TestCase.assertEquals(2, DataModel.getCategories().size)
    }

    @Ignore // broken june/2024
    @Test
    fun verifyBothCategoriesShow() {
        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[0].assertIsDisplayed()
        categoryActivityTestRule.onAllNodesWithTag(TestTag.TEST_TAG_CATEGORY_ROW)[1].assertIsDisplayed()
    }

    @Test
    fun addCategory() {
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
            .performTextInput("newCategory")
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_CATEGORY_BUTTON)
            .performClick()
        categoryActivityTestRule.waitForIdle()
        // Don't have a full mock..just verify the putCategoryEntry is called!
        verify(exactly = 0) { runBlocking { DataModel.deleteCategory(any()) } }
        verify(exactly = 1) { runBlocking { DataModel.addOrEditCategory(any()) } }
    }

    @Test
    fun trySearch() {
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_TOP_ACTION_BAR_SEARCH)
            .performClick()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .assertIsDisplayed()
        categoryActivityTestRule.onNodeWithTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD)
            .performTextInput("3")
        categoryActivityTestRule.waitForIdle()
        // eh.. these are in PasswordSearchScreen into which categoryActivityTestRule has no access
        //categoryActivityTestRule.onAllNodesWithTag(TESTTAG_SEARCH_MATCH).assertCountEquals(1)
    }

    @Ignore // broken june/2024
    @Test
    fun deleteCategoryCancel() {
        TestCase.assertEquals(2, DataModel.getCategories().size)
        TestCase.assertEquals(2, DataModel.getCategories().size)
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
        categoryActivityTestRule.waitForIdle()
        verify(exactly = 0) { runBlocking { DataModel.deleteCategory(any()) } }
        verify(exactly = 0) { runBlocking { DataModel.addOrEditCategory(any()) } }
    }

    @Ignore // broken june/2024
    @Test
    fun deleteCategory() {
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
        verify(exactly = 1) { runBlocking { DataModel.deleteCategory(any()) } }
        verify(exactly = 0) { runBlocking { DataModel.addOrEditCategory(any()) } }
    }

    companion object {
        var ks: KeyStoreHelper? = null
        private fun makePwd(
            ks: KeyStoreHelper,
            categoryId: Long,
            id: Long
        ): DecryptableSiteEntry {
            val passwordEntry = DecryptableSiteEntry(categoryId)
            passwordEntry.id = id
            passwordEntry.description = ks.encryptByteArray("enc_pwd${id}".toByteArray())
            passwordEntry.username = ks.encryptByteArray("enc_user${id}".toByteArray())
            passwordEntry.website = ks.encryptByteArray("enc_web${id}".toByteArray())
            passwordEntry.note = ks.encryptByteArray("enc_note${id}".toByteArray())
            passwordEntry.password = ks.encryptByteArray("enc_pwd${id}".toByteArray())
//            passwordEntry.passwordChangedDate = Date(1685571707000L)
            return passwordEntry
        }

        // Such a pain, mock needs to be done before @rule (coz we use prefs at LoginScreen)
        @BeforeClass
        @JvmStatic
        fun setup() {
            mockkObject(Preferences)
            every { BiometricsActivity.isBiometricEnabled() } returns false

            // "disable" login screen by faking we're logged in
            mockKeyStoreHelper()
            ks = KeyStoreHelperFactory.getKeyStoreHelper()
            mockDataModel(ks!!)
        }

        private fun mockDataModel(ks: KeyStoreHelper) {
            mockkObject(DataModel)
            every { DataModel.getCategories() } returns listOf(
                DecryptableCategoryEntry().let {
                    it.id = 1
                    it.encryptedName = ks.encryptByteArray("one".toByteArray())
                    it
                },
                DecryptableCategoryEntry().let {
                    it.id = 2
                    it.encryptedName = ks.encryptByteArray("two".toByteArray())
                    it
                }
            )
            every { DataModel.getCategorysPasswords(any()) } returns listOf(
                makePwd(ks, 1L, 1L),
                makePwd(ks, 2L, 2L)
            )
            every { DataModel.getPasswords() } returns listOf(
                makePwd(ks, 1L, 1L),
                makePwd(ks, 2L, 2L)
            )

            val dbHelper =
                DBHelperFactory.getDBHelper(InstrumentationRegistry.getInstrumentation().targetContext)
            DataModel.attachDBHelper(dbHelper)
            TestCase.assertEquals(2, DataModel.getCategories().size)
        }

        // TODO: Make shared with unit tests and other mocks
        private fun mockKeyStoreHelper() {
            val p = mockkClass(KeyStoreHelper::class)
            mockkObject(KeyStoreHelperFactory)
            every { KeyStoreHelperFactory.getKeyStoreHelper() } returns p
            val encryptionInput = slot<ByteArray>()
            every { p.encryptByteArray(capture(encryptionInput)) } answers {
                IVCipherText(ByteArray(CipherUtilities.IV_LENGTH), encryptionInput.captured)
            }
            val decryptionInput = slot<IVCipherText>()
            every { p.decryptByteArray(capture(decryptionInput)) } answers {
                decryptionInput.captured.cipherText
            }

            mockkObject(LoginHandler)
            every { LoginHandler.isLoggedIn() } returns true
        }
    }
}