package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.gpm.ui.models.ItemWrapper
import fi.iki.ede.safe.ui.theme.SafeListItem

@Composable
fun Page4(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Obsolete passwords(will be DELETED). Ie. identified not to exist in new import.")
        val selectableList =
            importChangeSet.value?.nonMatchingSavedGPMsToDelete?.toList() ?: emptyList()
        val wrappedList = selectableList.map { ItemWrapper(it) }
        val showInfo = remember { mutableStateOf<SavedGPM?>(null) }
        if (showInfo.value != null) {
            ShowInfo(showInfo.value!!, onDismiss = { showInfo.value = null })
        }
        LazyColumn {
            items(wrappedList) { entry ->
                SafeListItem {
                    SelectableItem(entry.item.cachedDecryptedName, { showInfo.value = entry.item })
                }
            }
        }
    }