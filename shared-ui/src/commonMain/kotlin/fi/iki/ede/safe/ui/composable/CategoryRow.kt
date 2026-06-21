package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.safe.ui.dialogs.DeleteCategoryDialog
import fi.iki.ede.theme.SafeListItem
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRow(
    category: DecryptableCategoryEntry,
    onCategoryClick: (DecryptableCategoryEntry) -> Unit,
    onRenameCategory: (DecryptableCategoryEntry, String) -> Unit,
    onDeleteCategory: (DecryptableCategoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayDeleteCategory = remember { mutableStateOf(false) }
    val displayEditDialog = remember { mutableStateOf(false) }
    val displayMenu = remember { mutableStateOf(false) }

    SafeListItem(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        onCategoryClick(category)
                    },
                    onLongClick = {
                        displayMenu.value = true
                    }
                )
                .testTag(TestTag.CATEGORY_ROW)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.plainName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = getPluralString("category_passwords_count", category.containedSiteEntryCount, category.containedSiteEntryCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = displayMenu.value,
            onDismissRequest = { displayMenu.value = false }
        ) {
            if (category.containedSiteEntryCount == 0) {
                DropdownMenuItem(
                    text = {
                        Text(text = getString("category_list_delete", category.plainName))
                    },
                    onClick = {
                        displayMenu.value = false
                        displayDeleteCategory.value = true
                    },
                    modifier = Modifier.testTag(TestTag.CATEGORY_ROW_DELETE)
                )
            }
            DropdownMenuItem(
                text = {
                    Text(text = getString("category_list_edit", category.plainName))
                },
                onClick = {
                    displayMenu.value = false
                    displayEditDialog.value = true
                },
                modifier = Modifier.testTag(TestTag.CATEGORY_ROW_EDIT)
            )
        }
        if (displayEditDialog.value) {
            AddOrEditCategory(
                titleText = getString("category_list_edit_category"),
                categoryName = category.plainName,
                onSubmit = {
                    if (category.plainName != it && it.isNotBlank()) {
                        onRenameCategory(category, it)
                    }
                    displayEditDialog.value = false
                }
            )
        }
        if (displayDeleteCategory.value) {
            DeleteCategoryDialog(
                category = category,
                onConfirm = {
                    onDeleteCategory(category)
                    displayDeleteCategory.value = false
                },
                onDismiss = {
                    displayDeleteCategory.value = false
                }
            )
        }
    }
}
