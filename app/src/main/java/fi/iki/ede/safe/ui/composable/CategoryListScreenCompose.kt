package fi.iki.ede.safe.ui.composable

import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.R
import fi.iki.ede.theme.SafeTheme
import fi.iki.ede.theme.SafeThemeSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
@Suppress("FlowOperatorInvokedInComposition")
internal fun CategoryListScreenCompose(
    flow: StateFlow<List<DecryptableCategoryEntry>> = MutableStateFlow(
        emptyList()
    )
) {
    val coroutineScope = rememberCoroutineScope()
    // TODO: Either new kotlin, coroutines or both, this is a linter error now
    val categoriesState by flow.map { categories -> categories.sortedBy { it.plainName.lowercase() } }
        .collectAsState(initial = emptyList())
    var displayAddCategoryDialog by remember { mutableStateOf(false) }

    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TopActionBar(
                    onAddRequested = {
                        displayAddCategoryDialog = true
                    },
                )
                if (displayAddCategoryDialog) {
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
                            displayAddCategoryDialog = false
                        })
                }
                CategoryList(categoriesState)
            }
        }
    }
}

@DualModePreview
@Composable
private fun CategoryListScreenComposePreview() {
    SafeThemeSurface {
        val categories = remember {
            KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
            KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
            val encrypter = KeyStoreHelperFactory.getEncrypter()
            val fakeCategories = listOf(
                DecryptableCategoryEntry().apply {
                    id = 1
                    encryptedName = encrypter("Bank".toByteArray())
                },
                DecryptableCategoryEntry().apply {
                    id = 2
                    encryptedName = encrypter("E-Mail".toByteArray())
                },
                DecryptableCategoryEntry().apply {
                    id = 3
                    encryptedName = encrypter("Social Media".toByteArray())
                }
            )
            MutableStateFlow(fakeCategories)
        }
        CategoryListScreenCompose(flow = categories)
    }
}
