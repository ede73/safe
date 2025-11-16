package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun SiteEntryExtensionSelector(
    viewModel: EditingSiteEntryViewModel,
    allKnownValues: Set<String>,
    extensionType: String,
) {
    val entry by viewModel.editableSiteEntryState.collectAsState()

    fun addToMap(
        map: Map<String, Set<String>>,
        type: String,
        value: String
    ): Map<String, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.plus(value) ?: setOf(value)
        return mutableMap.toMap()
    }

    fun removeFromMap(
        map: Map<String, Set<String>>,
        type: String,
        value: String
    ): Map<String, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.minus(value) ?: emptySet()
        return mutableMap
    }

    val allKnownEntries =
        remember { mutableStateListOf<String>().also { it.addAll(allKnownValues) } }
    val checked = remember { mutableStateOf(false) }
    if (!entry.plainExtension.containsKey(extensionType)) {
        entry.plainExtension = entry.plainExtension.toMutableMap().apply {
            this[extensionType] = setOf()
        }
    }
    val selectedEntry = remember { mutableStateOf("") }

    if (entry.plainExtension[extensionType]!!.isEmpty() && !checked.value) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked.value,
                onCheckedChange = { checked.value = !checked.value },
                modifier = Modifier.testTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY_CHECKBOX)
            )
            Text(text = extensionType)
        }
    } else {
        Text(text = extensionType)
        EditableComboBox(
            selectedItems = entry.plainExtension[extensionType]!!.toSet(),
            allItems = allKnownEntries.toSet(),
            onItemSelected = { selectedItem ->
                val currentExtension = entry.plainExtension
                val currentSet = currentExtension[extensionType] ?: emptySet()
                if (selectedItem in currentSet) {
                    viewModel.updateExtensions(
                        removeFromMap(
                            currentExtension,
                            extensionType,
                            selectedItem
                        )
                    )
                    ""
                } else {
                    viewModel.updateExtensions(
                        addToMap(
                            currentExtension,
                            extensionType,
                            selectedItem
                        )
                    )
                    selectedItem
                }
            },
            onItemEdited = { editedItem ->
                val rem = removeFromMap(
                    entry.plainExtension,
                    extensionType,
                    selectedEntry.value
                )
                val add = addToMap(rem, extensionType, editedItem)
                viewModel.updateExtensions(add)
                selectedEntry.value = editedItem
            },
            onItemRequestedToDelete = { itemToDelete ->
                // ONLY can delete if NOT used anywhere else
                if (!DataModel.getAllSiteEntryExtensions(entry.id).flatMap { it.value }.toSet()
                        .contains(itemToDelete)
                ) {
                    // deletion allowed, not used anywhere else..will "autodelete" from full collection on save
                    entry.plainExtension[extensionType].let { currentSet ->
                        if (itemToDelete in currentSet!!) {
                            viewModel.updateExtensions(
                                removeFromMap(
                                    entry.plainExtension,
                                    extensionType,
                                    itemToDelete
                                ),
                            )
                        }
                    }
                    allKnownEntries.remove(itemToDelete)
                }
            })
    }
}