@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.theme.SafeListItem
import fi.iki.ede.crypto.support.encrypt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryRow(category: DecryptableCategoryEntry) {
    val db = remember { fi.iki.ede.db.DBHelperFactory.getDBHelper() }
    var displayMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf(category.plainName) }

    SafeListItem {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        DesktopNavigation.activeCategory = category
                    },
                    onLongClick = {
                        displayMenu = true
                    }
                )
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
                    text = "${category.containedSiteEntryCount} passwords",
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
            expanded = displayMenu,
            onDismissRequest = { displayMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename Category") },
                onClick = {
                    displayMenu = false
                    showRenameDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("Delete Category") },
                onClick = {
                    displayMenu = false
                    db.deleteCategory(category.id!!)
                    DesktopNavigation.dbRefreshTrigger++
                }
            )
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Category") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                val entry = DecryptableCategoryEntry().apply {
                                    id = category.id
                                    encryptedName = newCategoryName.encrypt()
                                }
                                db.updateCategory(category.id!!, entry)
                                DesktopNavigation.dbRefreshTrigger++
                                showRenameDialog = false
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
