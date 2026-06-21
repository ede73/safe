@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.categorypager

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.datamodel.DataModel.siteEntriesStateFlow
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.ui.composable.CategoryRow
import fi.iki.ede.safe.ui.composable.SiteEntryList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime


import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.safe.splits.IntentManager

@ExperimentalTime
@ExperimentalFoundationApi
class CategoryListPagedScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CategoryListScreenPagedCompose(DataModel.categoriesStateFlow) }
    }
}

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun CategoryListScreenPagedCompose(
    flow: StateFlow<List<DecryptableCategoryEntry>> = MutableStateFlow(
        emptyList()
    )
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    @Suppress("FlowOperatorInvokedInComposition")
    val categoriesState by flow.map { categories -> categories.sortedBy { it.plainName.lowercase() } }
        .collectAsState(initial = emptyList())
    val displayAddCategoryDialog = remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { categoriesState.size })

    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            HorizontalPager(state = pagerState) { page ->
                val category = categoriesState[page]
                val passwordsState by siteEntriesStateFlow
                    .map { passwords -> passwords.filter { it.categoryId == category.id } }
                    .map { passwords -> passwords.sortedBy { it.cachedPlainDescription.lowercase() } }
                    .filterNotNull()
                    .collectAsState(initial = emptyList())
                Column(modifier = Modifier.fillMaxSize()) {
                    TopActionBar(onAddRequested = { displayAddCategoryDialog.value = true })
                    if (displayAddCategoryDialog.value) {
                        AddOrEditCategory(coroutineScope, displayAddCategoryDialog)
                    }
                    CategoryRow(
                        category = category,
                        onCategoryClick = { cat ->
                            IntentManager.startSiteEntryListScreen(context, cat.id!!)
                        },
                        onRenameCategory = { cat, newName ->
                            val entry = DecryptableCategoryEntry()
                            entry.id = cat.id
                            entry.encryptedName = newName.encrypt()
                            coroutineScope.launch {
                                DataModel.addOrEditCategory(entry)
                            }
                        },
                        onDeleteCategory = { cat ->
                            coroutineScope.launch {
                                DataModel.deleteCategory(cat)
                            }
                        }
                    )
                    SiteEntryList(
                        siteEntries = passwordsState,
                        categoriesState = categoriesState,
                        onSiteEntryClick = { siteEntry ->
                            context.startActivity(IntentManager.getEditSiteEntryIntent(context, siteEntry.id!!))
                        },
                        onDeleteSiteEntry = { siteEntry ->
                            coroutineScope.launch {
                                DataModel.deleteSiteEntry(siteEntry)
                            }
                        },
                        onMoveSiteEntry = { siteEntry, newCategory ->
                            coroutineScope.launch {
                                DataModel.moveSiteEntry(siteEntry, newCategory)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddOrEditCategory(
    coroutineScope: CoroutineScope,
    displayAddCategoryDialog: MutableState<Boolean>
) {
    AddOrEditCategory(
        // Addressed PR12 comment: Cleaned up FQCN and imported stringResource
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

@Preview(showBackground = true)
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun CategoryListPagedScreenPreview() {
    MockKeyStoreHelper.init()
    val flow = listOf(DecryptableCategoryEntry().apply {
        encryptedName = "Android".encrypt()
    }, DecryptableCategoryEntry().apply {
        encryptedName = "iPhone".encrypt()
    })
    CategoryListScreenPagedCompose(MutableStateFlow(flow))
}