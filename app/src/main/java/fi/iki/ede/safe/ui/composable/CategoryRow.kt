package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.DecryptableCategoryEntry
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.ui.activities.CategoryListScreen
import fi.iki.ede.safe.ui.activities.PasswordListScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRow(category: DecryptableCategoryEntry) {
    val context = LocalContext.current
    var displayMenu by remember { mutableStateOf(false) }
    var displayDeleteCategory by remember { mutableStateOf(false) }
    var displayEditDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(modifier = Modifier.padding(6.dp), shape = RoundedCornerShape(20.dp)) {
        Row {
            Text(text = "${category.plainName}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            PasswordListScreen.startMe(context, category.id!!)
                        },
                        onLongClick = {
                            // Creating a dropdown menu
                            displayMenu = true
                        }
                    )
                    .fillMaxWidth()
                    .padding(12.dp)
                    .weight(1f)
                    .testTag(CategoryListScreen.TESTTAG_CATEGORY_ROW)
            )
            Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end
            Text(
                text = "(${category.containedPasswordCount})",
                modifier = Modifier.padding(12.dp)
            )
        }
        DropdownMenu(
            expanded = displayMenu,
            onDismissRequest = { displayMenu = false }
        ) {
            if (category.containedPasswordCount == 0) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                id = R.string.category_list_delete,
                                category.plainName
                            )
                        )
                    },
                    onClick = {
                        displayMenu = false
                        displayDeleteCategory = true
                    },
                    modifier = Modifier.testTag(CategoryListScreen.TESTTAG_CATEGORY_ROW_DELETE)
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(
                            id = R.string.category_list_edit,
                            category.plainName
                        )
                    )
                },
                onClick = {
                    displayMenu = false
                    displayEditDialog = true
                },
                modifier = Modifier.testTag(CategoryListScreen.TESTTAG_CATEGORY_ROW_EDIT)
            )
        }
        if (displayEditDialog) {
            val ks = KeyStoreHelperFactory.getKeyStoreHelper()
            AddOrEditCategory(
                textId = R.string.category_list_edit_category,
                categoryName = category.plainName,
                onSubmit = {
                    if (category.plainName != it) {
                        val entry = DecryptableCategoryEntry()
                        entry.id = category.id
                        entry.encryptedName = ks.encryptByteArray(it.toByteArray())
                        coroutineScope.launch {
                            DataModel.addOrEditCategory(entry)
                        }
                    }
                    displayEditDialog = false
                })
        }
        if (displayDeleteCategory) {
            DeleteCategoryDialog(category, onConfirm = {
                runBlocking {
                    DataModel.deleteCategory(category)
                }
                displayDeleteCategory = false
            }, onDismiss = {
                displayDeleteCategory = false
            })
        }
    }
}