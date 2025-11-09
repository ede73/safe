package fi.iki.ede.categorypager

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
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
                    CategoryRow(category)
                    SiteEntryList(passwordsState)
                }
            }
        }
    }
}

@Composable
@ExperimentalTime

private fun AddOrEditCategory(
    coroutineScope: CoroutineScope,
    displayAddCategoryDialog: MutableState<Boolean>
) {
    val encrypter = KeyStoreHelperFactory.getEncrypter()
    AddOrEditCategory(
        textId = R.string.category_list_edit_category,
        categoryName = "",
        onSubmit = {
            if (!TextUtils.isEmpty(it)) {
                val entry = DecryptableCategoryEntry().apply {
                    encryptedName = encrypter(it.toByteArray())
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
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    val flow = listOf(DecryptableCategoryEntry().apply {
        encryptedName = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableCategoryEntry().apply {
        encryptedName = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    CategoryListScreenPagedCompose(MutableStateFlow(flow))
}