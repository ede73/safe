package fi.iki.ede.safe.ui.composable

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.SharedLoginScreen
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.theme.SafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.ExperimentalTime

@ComboPreview
@Composable
fun SharedLoginScreenPreview(title: String = "1. Login Screen Card") {
    PreviewPreview(title) {
        SharedLoginScreen(
            isFirstTimeLogin = false,
            isBiometricsEnabled = true,
            statusMessage = "Status message demo",
            onCreateVault = { _, _ -> },
            onUnlock = { _, _ -> },
            onBiometricLogin = {}
        )
    }
}

// ======= Category List

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@ComboPreview
@Composable
fun CategoryListScreenPreview(title: String = "2. Category Screen") {
    PreviewPreview(title) {
        CategoryListScreenCompose(MutableStateFlow(ShowcaseMockData.fakeCategories))
    }
}

@ComboPreview
@Composable
fun CategoryListPreview(title: String = "3. Category List(solo)") {
    PreviewPreview(title) {
        CategoryList(
            modifier = Modifier.height(300.dp),
            categories = ShowcaseMockData.fakeCategories,
            onCategoryClick = {},
            onRenameCategory = { _, _ -> },
            onDeleteCategory = {}
        )
    }
}

// ======= Site Entry List

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@ComboPreview
@Composable
fun SiteEntryListScreenPreview(title: String = "3. Site Entry List Screen") {
    PreviewPreview(title) {
        SiteEntryListCompose(
            null,
            ShowcaseMockData.fakeCategories.first(),
            ShowcaseMockData.fakeSiteEntries
        )
    }
}


@ComboPreview
@Composable
fun SiteEntryListPreview(title: String = "5. Site Entry List(solo)") {
    PreviewPreview(title) {
        SiteEntryList(
            modifier = Modifier.height(320.dp),
            siteEntries = ShowcaseMockData.fakeSiteEntries,
            categoriesState = ShowcaseMockData.fakeCategories,
            onSiteEntryClick = {},
            onDeleteSiteEntry = {}
        )
    }
}

// ======= Site Entry Edit

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@ComboPreview
@Composable
fun SiteEntryEditScreenPreview(title: String = "6. Site Entry Edit Screen") {
    PreviewPreview(title) {
        class FakeEditingSiteViewModel : EditingSiteEntryViewModel()

        val fakeViewModel = FakeEditingSiteViewModel().apply {
            // Set up the ViewModel with test data as needed
            editSiteEntry(ShowcaseMockData.fakeSiteEntries.first())
        }
        SiteEntryEditCompose(
            fakeViewModel,
            1,
            { _, _ -> true to true },
            { _, _ -> },
            {},
            skipForPreviewToWork = true
        )
    }
}

// ======= Site Entry Search Screen

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@ComboPreview
@Composable
fun SiteEntrySearchScreenPreview(title: String = "7. Search Screen") {
    PreviewPreview(title) {
        SiteEntrySearchCompose()
    }
}

// ================
// ======= support
// ================

@Composable
private fun PreviewPreview(title: String, content: @Composable () -> Unit) {
    MockKeyStoreHelper.init()
    SafeTheme {
        SafeThemeSurface {
            ShowcaseSectionHeader(title)
            content()
        }
    }
}

@Composable
private fun ShowcaseSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = SafeTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Preview(showBackground = true, name = "Light Mode Showcase")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode Showcase",
)
annotation class ComboPreview

// Mock Data
private object ShowcaseMockData {
    val fakeCategories = listOf(
        DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = "Cards / Banks / Finance".encrypt()
            containedSiteEntryCount = 43
        },
        DecryptableCategoryEntry().apply {
            id = 2
            encryptedName = "DNA/Genealogy".encrypt()
            containedSiteEntryCount = 15
        },
        DecryptableCategoryEntry().apply {
            id = 3
            encryptedName = "Google Password Manager".encrypt()
            containedSiteEntryCount = 22
        }
    )

    val fakeSiteEntries = listOf(
        DecryptableSiteEntry(1L).apply {
            id = 101L
            description = "Facebook".encrypt()
            username = "user@example.com".encrypt()
        },
        DecryptableSiteEntry(1L).apply {
            id = 102L
            description = "Twitter / X".encrypt()
            username = "some_handle".encrypt()
        },
        DecryptableSiteEntry(1L).apply {
            id = 103L
            description = "Google Account".encrypt()
            username = "mygoogleuser".encrypt()
        }
    )
}
