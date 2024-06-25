package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.safe.gpm.ui.models.ItemWrapper
import fi.iki.ede.safe.ui.theme.SafeListItem

@Composable
fun Page3(importChangeSet: MutableState<ImportChangeSet?>) =
    Column {
        Text("Conflicts - incoming matches multiple existing ones. We'll do nothing to sort these. They're included in other sets though.")
        Column {
            val selectableList =
                importChangeSet.value?.getMatchingConflicts?.toList() ?: emptyList()
            val wrappedList = selectableList.map {
                Pair(
                    ItemWrapper(it.first),
                    it.second.map { element -> ItemWrapper(element) }.toSet()
                )
            }

            HorizontalDivider()
            Row {
                Text("Incoming GPM")
                Spacer(modifier = Modifier.weight(0.5f))
                Text("Existing GPMs")
            }
            val showInfo1 = remember { mutableStateOf<IncomingGPM?>(null) }
            if (showInfo1.value != null) {
                ShowInfo(showInfo1.value!!, onDismiss = { showInfo1.value = null })
            }
            val showInfo2 = remember { mutableStateOf<ScoredMatch?>(null) }
            if (showInfo2.value != null) {
                ShowInfo(showInfo2.value!!, onDismiss = { showInfo2.value = null })
            }
            LazyColumn {
                wrappedList.forEach { entry ->
                    item {
                        SafeListItem {
                            SelectableItem(
                                entry.first.item.name, { showInfo1.value = entry.first.item })
                        }
                    }
                    items(entry.second.toList()) { value ->
                        SafeListItem {
                            SelectableItem(
                                value.item.item.cachedDecryptedName,
                                { showInfo2.value = value.item },
                                true
                            )
                        }
                    }
                }
            }
        }
    }