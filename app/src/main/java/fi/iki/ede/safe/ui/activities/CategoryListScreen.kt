package fi.iki.ede.safe.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class CategoryListScreen : AutolockingBaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val categoriesState by DataModel.categoriesStateFlow
                .map { categories -> categories.sortedBy { it.plainName.lowercase() } }
                .collectAsState(initial = emptyList())
            var displayAddCategoryDialog by remember { mutableStateOf(false) }

            SafeTheme {
                // A surface container using the 'background' color from the theme
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
                            })
                        if (displayAddCategoryDialog) {
                            val ks = KeyStoreHelperFactory.getKeyStoreHelper()
                            AddOrEditCategory(
                                textId = R.string.category_list_edit_category,
                                categoryName = "",
                                onSubmit = {
                                    if (!TextUtils.isEmpty(it)) {
                                        val entry = DecryptableCategoryEntry()
                                        entry.encryptedName = ks.encryptByteArray(it.toByteArray())
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
    }

    companion object {
        // TODO: RELOCATE this to respective places in composables themselves...
        const val TESTTAG_CATEGORY_ROW = "categoryrow"
        const val TESTTAG_CATEGORY_MOVE_ROW = "category_move_row"
        const val TESTTAG_CATEGORY_BUTTON = "addcategory_button"
        const val TESTTAG_CATEGORY_TEXTFIELD = "addcategory_textfield"
        const val TESTTAG_CATEGORY_ROW_DELETE = "categorylist_row_delete"
        const val TESTTAG_CATEGORY_ROW_EDIT = "categorylist_row_edit"
        const val TESTTAG_CATEGORY_ROW_DELETE_CONFIRM = "categorylist_row_delete_confirm"
        const val TESTTAG_CATEGORY_ROW_DELETE_CANCEL = "categorylist_row_delete_cancel"
        fun startMe(context: Context) {
            context.startActivity(
                Intent(
                    context,
                    CategoryListScreen::class.java
                ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun CategoryScreenPreview() {
//    SafeTheme {
//        Column(
//            modifier = Modifier.fillMaxSize(),
//            verticalArrangement = Arrangement.SpaceBetween
//        ) {
//            CategoryList(
//                listOf(
//                    DecryptableCategoryEntry("Android"),
//                    DecryptableCategoryEntry("Something else")
//                )
//            )
//            TopActionBar()
//        }
//    }
//}