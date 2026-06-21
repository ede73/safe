package fi.iki.ede.gpmui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.model.IncomingGPM
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.preferences.setPreferencesContext
import fi.iki.ede.gpmui.dialogs.ShowInfoDialog
import fi.iki.ede.gpmui.models.ItemWrapper
import fi.iki.ede.gpmui.utilities.makeFakeImportForTesting
import fi.iki.ede.theme.SafeListItem

@Composable
fun MatchingGpmsToBeUpdatedPage(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Matching passwords(will be UPDATED). Ie. matched incoming to existing.")
        val selectableList =
            importChangeSet.value?.getNonConflictingGPMs?.toList() ?: emptyList()
        val wrappedList = selectableList.map { ItemWrapper(it) }
        val showInfo = remember { mutableStateOf<IncomingGPM?>(null) }
        if (showInfo.value != null) {
            ShowInfoDialog(showInfo.value!!, onDismiss = { showInfo.value = null })
        }
        LazyColumn {
            items(wrappedList) { entry ->
                SafeListItem {
                    SelectableItem(entry.item.first.name, { showInfo.value = entry.item.first })
                }
            }
        }
    }

@OptIn(kotlin.time.ExperimentalTime::class)
@Preview(showBackground = true)
@Composable
private fun Page2Preview() {
    // Addressed PR12 comment: Initialize preferences cleanly without reflection mock helper
    setPreferencesContext(LocalContext.current)
    Preferences.initialize()
    MaterialTheme {
        val m =
            remember { mutableStateOf<ImportChangeSet?>(makeFakeImportForTesting()) }
        MatchingGpmsToBeUpdatedPage(m)
    }
}