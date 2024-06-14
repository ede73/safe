package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRow(category: DecryptableCategoryEntry) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val safeTheme = LocalSafeTheme.current
    var displayDeleteCategory by remember { mutableStateOf(false) }
    var displayEditDialog by remember { mutableStateOf(false) }
    var displayMenu by remember { mutableStateOf(false) }

    SafeListItem {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        IntentManager.startSiteEntryListScreen(context, category.id!!)
                    },
                    onLongClick = {
                        // Creating a dropdown menu
                        displayMenu = true
                    }
                )
                .testTag(TestTag.TEST_TAG_CATEGORY_ROW),
        ) {
            Text(
                text = "${category.plainName}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .weight(2f),
                style = safeTheme.customFonts.listEntries
            )
            Spacer(modifier = Modifier.weight(1f)) // This will push the Text to the end
            Text(
                text = "(${category.containedPasswordCount})",
                modifier = Modifier.padding(12.dp),
                style = safeTheme.customFonts.smallNote,
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
                                id = R.string.category_list_delete, category.plainName
                            )
                        )
                    },
                    onClick = {
                        displayMenu = false
                        displayDeleteCategory = true
                    },
                    modifier = Modifier.testTag(TestTag.TEST_TAG_CATEGORY_ROW_DELETE)
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(id = R.string.category_list_edit, category.plainName)
                    )
                },
                onClick = {
                    displayMenu = false
                    displayEditDialog = true
                },
                modifier = Modifier.testTag(TestTag.TEST_TAG_CATEGORY_ROW_EDIT)
            )
        }
        if (displayEditDialog) {
            val encrypter = KeyStoreHelperFactory.getEncrypter()
            AddOrEditCategory(
                textId = R.string.category_list_edit_category,
                categoryName = category.plainName,
                onSubmit = {
                    if (category.plainName != it) {
                        val entry = DecryptableCategoryEntry()
                        entry.id = category.id
                        entry.encryptedName = encrypter(it.toByteArray())
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

@Preview(showBackground = true)
@Composable
fun CategoryRowPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val cat = DecryptableCategoryEntry().apply {
            encryptedName = encrypter("Category".toByteArray())
        }
        CategoryRow(category = cat)
    }
}