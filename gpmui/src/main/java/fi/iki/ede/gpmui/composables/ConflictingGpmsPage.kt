package fi.iki.ede.gpmui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.changeset.ImportChangeSet
import fi.iki.ede.gpm.changeset.ScoredMatch
import fi.iki.ede.gpm.model.IncomingGPM
import fi.iki.ede.gpmui.dialogs.ShowInfoDialog
import fi.iki.ede.gpmui.models.ItemWrapper
import fi.iki.ede.gpmui.utilities.makeFakeImportForTesting
import fi.iki.ede.theme.SafeListItem

@Composable
fun ConflictingGpmsPage(importChangeSet: MutableState<ImportChangeSet?>) =
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
                ShowInfoDialog(showInfo1.value!!, onDismiss = { showInfo1.value = null })
            }
            val showInfo2 = remember { mutableStateOf<ScoredMatch?>(null) }
            if (showInfo2.value != null) {
                ShowInfoDialog(showInfo2.value!!, onDismiss = { showInfo2.value = null })
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

@Preview(showBackground = true)
@Composable
fun Page3Preview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    MaterialTheme {
        val m =
            remember { mutableStateOf<ImportChangeSet?>(makeFakeImportForTesting()) }
        ConflictingGpmsPage(m)
    }
}