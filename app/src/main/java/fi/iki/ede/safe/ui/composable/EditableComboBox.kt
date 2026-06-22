package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.SafeThemeSurface
import kotlin.time.ExperimentalTime

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun EditableComboBox(
    selectedItems: Set<String>, // SET should suffice! Hoist to top side(.remove)
    allItems: Set<String>,
    onItemSelected: (String) -> String,
    onItemEdited: (String) -> Unit,
    onItemRequestedToDelete: (String) -> Unit,
    onItemCommitted: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = remember { mutableStateOf("") }
    val showDeleteDialog = remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf("") }

    LaunchedEffect(selectedItems) {
    }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {},
        ) {
            TextField(
                modifier = Modifier
                    .menuAnchor()
                    .testTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY),
                value = selectedText.value,
                onValueChange = {
                    selectedText.value = it
                    onItemEdited(it)
                },
                readOnly = expanded,
                singleLine = true,
                label = { Text(stringResource(id = R.string.site_entry_extension_entry_label)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedText.value.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.generic_add),
                                modifier = Modifier.clickable {
                                    onItemCommitted()
                                    selectedText.value = ""
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.dropdown_description),
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (selectedText.value.isNotBlank()) {
                            onItemCommitted()
                            selectedText.value = ""
                        }
                    }
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                allItems.forEach { menuItemText ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            selectedText.value = onItemSelected(menuItemText)
                                            expanded = false
                                        },
                                        onLongClick = {
                                            itemToDelete = menuItemText
                                            showDeleteDialog.value = true
                                        },
                                    )
                                    .fillMaxWidth()
                            ) {
                                if (selectedItems.contains(menuItemText)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.checkmark_description)
                                    )
                                }
                                Text(
                                    menuItemText,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        },
                        onClick = {
                            selectedText.value = menuItemText
                            onItemSelected(menuItemText)
                            expanded = false
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        if (selectedItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                selectedItems.forEach { item ->
                    if (item.isNotBlank()) {
                        ExtensionChip(
                            text = item,
                            onDelete = {
                                onItemSelected(item)
                            },
                            onClick = {
                                onItemCommitted()
                                selectedText.value = item
                                onItemSelected(item)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text(stringResource(id = R.string.site_entry_extension_delete)) },
            text = { Text(stringResource(id = R.string.site_entry_extension_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onItemRequestedToDelete(itemToDelete)
                        showDeleteDialog.value = false
                    }
                ) {
                    Text(stringResource(id = R.string.site_entry_extension_delete))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog.value = false }) {
                    Text(stringResource(id = R.string.site_entry_extension_cancel))
                }
            }
        )
    }
}

@Composable
fun ExtensionChip(
    text: String,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.remove_description),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDelete),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
private fun EditableComboBoxPreview() {
    SafeThemeSurface {
        EditableComboBox(
            selectedItems = setOf("previous1"),
            allItems = setOf("previous1", "previous2"),
            onItemSelected = { "" },
            onItemEdited = { _ -> },
            onItemRequestedToDelete = {},
            onItemCommitted = {}
        )
    }
}