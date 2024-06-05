package fi.iki.ede.safe

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.activities.SiteEntryListScreen
import fi.iki.ede.safe.utilities.LoggedOutTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// test that main interfaces support automatic transition to login screen
// if for what ever reason (backbuffer) launched logged out (auto logout)
// Alas there isn't easy way to make these dynamic due to @get:Rule)
@RunWith(AndroidJUnit4::class)
class SiteEntryListScreenStartLoggedOutTest : LoggedOutTest() {
    @get:Rule
    val composeTest = createAndroidComposeRule<SiteEntryListScreen>()

    @Test
    fun test() =
        transitionToLoginScreenIfNotLoggedIn(composeTest)
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
