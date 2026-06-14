package fi.iki.ede.safe.ui.composable

import android.text.TextUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.R
import fi.iki.ede.theme.SafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.composable.CategoryList

@ExperimentalFoundationApi
@Composable
@ExperimentalTime
@Suppress("FlowOperatorInvokedInComposition")
internal fun CategoryListScreenCompose(
    flow: StateFlow<List<DecryptableCategoryEntry>> = MutableStateFlow(
        emptyList()
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // TODO: Either new kotlin, coroutines or both, this is a linter error now
    val categoriesState by flow.map { categories -> categories.sortedBy { it.plainName.lowercase() } }
        .collectAsState(initial = emptyList())
    val displayAddCategoryDialog = remember { mutableStateOf(false) }

    SafeTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomActionBar(
                    onAddRequested = {
                        displayAddCategoryDialog.value = true
                    },
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding),
            ) {
                if (displayAddCategoryDialog.value) {
                    AddOrEditCategory(
                        titleText = stringResource(id = R.string.category_list_edit_category),
                        categoryName = "",
                        onSubmit = {
                            if (!TextUtils.isEmpty(it)) {
                                val entry = DecryptableCategoryEntry().apply {
                                    encryptedName = it.encrypt()
                                }
                                coroutineScope.launch {
                                    DataModel.addOrEditCategory(entry)
                                }
                            }
                            displayAddCategoryDialog.value = false
                        })
                }
                CategoryList(
                    categories = categoriesState,
                    onCategoryClick = { category ->
                        IntentManager.startSiteEntryListScreen(context, category.id!!)
                    },
                    onRenameCategory = { category, newName ->
                        val entry = DecryptableCategoryEntry()
                        entry.id = category.id
                        entry.encryptedName = newName.encrypt()
                        coroutineScope.launch {
                            DataModel.addOrEditCategory(entry)
                        }
                    },
                    onDeleteCategory = { category ->
                        coroutineScope.launch {
                            DataModel.deleteCategory(category)
                        }
                    }
                )
            }
        }
    }
}

@ExperimentalFoundationApi
@DualModePreview
@Composable
@ExperimentalTime
private fun CategoryListScreenComposePreview() {
    SafeThemeSurface {
        val categories = remember {
            MockKeyStoreHelper.init()
            val fakeCategories = listOf(
                DecryptableCategoryEntry().apply {
                    id = 1
                    encryptedName = "Bank".encrypt()
                    containedSiteEntryCount = 5
                },
                DecryptableCategoryEntry().apply {
                    id = 2
                    encryptedName = "E-Mail".encrypt()
                    containedSiteEntryCount = 3
                },
                DecryptableCategoryEntry().apply {
                    id = 3
                    encryptedName = "Social Media".encrypt()
                    containedSiteEntryCount = 12
                }
            )
            MutableStateFlow(fakeCategories)
        }
        CategoryListScreenCompose(flow = categories)
    }
}
