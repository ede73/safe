package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.gpm.model.encrypter
import fi.iki.ede.safe.R
import fi.iki.ede.safe.db.DBID
import fi.iki.ede.safe.gpm.ui.models.DNDObject
import fi.iki.ede.safe.gpm.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.gpm.ui.models.SiteEntryToGPM
import fi.iki.ede.safe.gpm.ui.modifiers.doesItHaveText
import fi.iki.ede.safe.gpm.ui.utilities.combineLists
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.dialogs.ShowInfoDialog
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.firebaseRecordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "ImportEntryList"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddIgnoreMergeGpmsAndSiteEntriesList(viewModel: ImportGPMViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showInfo = remember { mutableStateOf<SavedGPM?>(null) }
    if (showInfo.value != null) {
        ShowInfoDialog(showInfo.value!!, onDismiss = { showInfo.value = null })
    }

    fun ignoreSavedGPM(id: Long, maybeLinkedSiteEntry: Pair<DecryptableSiteEntry, SavedGPM>?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (maybeLinkedSiteEntry != null) {
                    viewModel.removeConnectedDisplayItem(
                        maybeLinkedSiteEntry.first,
                        maybeLinkedSiteEntry.second
                    )
                }
                DataModel.markSavedGPMIgnored(id)
            } catch (ex: Exception) {
                firebaseRecordException("ignoreSavedGPM $id failed", ex)
            }
        }
    }

    fun linkSavedGPMAndDecryptableSiteEntry(
        siteEntry: DecryptableSiteEntry,
        gpmId: Long,
        linkedSavedGPM: SavedGPM? = null,
    ) {
        try {
            // TODO: IN theory this should not be needed, data model will update flow
            // and thus the screen will be updated, but sorting order? location? gone?
            if (linkedSavedGPM != null) {
                viewModel.removeConnectedDisplayItem(siteEntry, linkedSavedGPM)
            } else {
                viewModel.removeAllMatchingGpmsFromDisplayAndUnprocessedLists(gpmId)
            }
            DataModel.linkSaveGPMAndSiteEntry(siteEntry, gpmId)
        } catch (ex: Exception) {
            firebaseRecordException(
                "linkSavedGPMAndDecryptableSiteEntry ${siteEntry.id} to $gpmId failed",
                ex
            )
        }
    }

    fun addSavedGPM(savedGPMId: Long, maybeLinkedSiteEntry: Pair<DecryptableSiteEntry, SavedGPM>?) {
        // TODO: Ask user the category, no goes to GPM cat
        val catId =
            DataModel.categoriesStateFlow.value.firstOrNull { it.plainName == "Google Password Manager" }

        suspend fun addNow(catId: DBID) {
            if (maybeLinkedSiteEntry != null) {
                viewModel.removeConnectedDisplayItem(
                    maybeLinkedSiteEntry.first,
                    maybeLinkedSiteEntry.second
                )
            }
            DataModel.addGpmAsSiteEntry(savedGPMId, categoryId = catId, onAdd = {
                linkSavedGPMAndDecryptableSiteEntry(it, savedGPMId)
            })
        }

        coroutineScope.launch {
            if (catId == null) {
                DataModel.addOrEditCategory(DecryptableCategoryEntry().apply {
                    encryptedName = "Google Password Manager".encrypt()
                }, onAdd = {
                    addNow(it.id!!)
                })
            } else {
                addNow(catId.id!!)
            }
        }
    }

    val connectedDisplayItems =
        viewModel.importMergeDataRepository.connectedDisplayItems.collectAsState()

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.height(intrinsicSize = IntrinsicSize.Max)
    ) {
        val mod = Modifier
            .weight(1f)
            .fillMaxHeight()
        DraggableText(
            DNDObject.JustString("Add"),
            modifier = mod,
            onItemDropped = { (_, maybeId) ->
                maybeId.toLongOrNull()?.let { savedGpmId ->
                    val maybeLinkedSiteEntry =
                        connectedDisplayItems.value.firstOrNull { it.second.id == savedGpmId }
                            ?.takeIf {
                                viewModel.displayedItemsAreConnected
                            }
                    addSavedGPM(savedGpmId, maybeLinkedSiteEntry)
                    true
                } ?: false
            }
        )

        SafeButton(
            modifier = mod,
            onClick = {
                coroutineScope.launch {
                    viewModel.importMergeDataRepository.resetSiteEntryDisplayListToAllSaved()
                }
            }) {
            Text(stringResource(R.string.google_password_import_merge_reset_password_list))
        }

        SafeButton(
            modifier = mod,
            onClick = {
                coroutineScope.launch {
                    viewModel.importMergeDataRepository.resetGPMDisplayListToAllUnprocessed()
                }
            }) {
            Text(
                stringResource(R.string.google_password_import_merge_reset_gpm_list),
                modifier = Modifier
                    .padding(0.dp),
            )
        }
        DraggableText(
            DNDObject.JustString("Ignore"),
            modifier = mod,
            onItemDropped = { (_, maybeId) ->
                maybeId.toLongOrNull()?.let { savedGpmId ->
                    val maybeLinkedSiteEntry =
                        connectedDisplayItems.value.firstOrNull { it.second.id == savedGpmId }
                            ?.takeIf {
                                viewModel.displayedItemsAreConnected
                            }
                    ignoreSavedGPM(savedGpmId, maybeLinkedSiteEntry)
                    true
                } ?: false
            }
        )
    }
    var lazyColumnTopY by remember { mutableFloatStateOf(0f) }

    val mySizeModifier = Modifier
        .fillMaxWidth(0.4f)
        .fillMaxHeight(0.2f)

    val listState = rememberLazyListState()
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onMoved(event: DragAndDropEvent) {
                super.onEntered(event)
                val absoluteDragY = event.toAndroidDragEvent().y
                val relativeDragY = absoluteDragY - lazyColumnTopY
                val lazyColumnSize =
                    listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                val threshold = lazyColumnSize * 0.1f // 10% threshold for auto-scroll
                when {
                    relativeDragY < threshold -> {
                        // Scroll up
                        coroutineScope.launch {
                            listState.scrollBy(-10f)
                        }
                    }

                    relativeDragY > lazyColumnSize - threshold -> {
                        // Scroll down
                        coroutineScope.launch {
                            listState.scrollBy(10f)
                        }
                    }
                }
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                return false
            }
        }
    }

    val displayedSiteEntries =
        viewModel.importMergeDataRepository.displayedSiteEntries.collectAsState()
    val displayedGPMs =
        viewModel.importMergeDataRepository.displayedUnprocessedGPMs.collectAsState()
    val combinedList = when (viewModel.displayedItemsAreConnected) {
        true -> connectedDisplayItems.value.map { SiteEntryToGPM(it.first, it.second, true) }
            .sortedWith(compareBy<SiteEntryToGPM, String?>(nullsLast()) {
                it.siteEntry?.cachedPlainDescription?.lowercase()
            }.thenBy {
                it.gpm?.cachedDecryptedName?.lowercase()
            })

        false -> combineLists(
            displayedSiteEntries.value,
            displayedGPMs.value,
        )
    }
    // something with LazyColumn not working right, make sure the list hash REALLY updates
    val listHash = "${combinedList.map { it.siteEntry }.toSet().hashCode()}-${
        combinedList.map { it.gpm }.toSet().hashCode()
    }-${combinedList.hashCode()}"

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> doesItHaveText(event) }, target = dndTarget
                )
                .onGloballyPositioned { coordinates ->
                    lazyColumnTopY = coordinates.positionInWindow().y
                }
        ) {
            items(
                combinedList,
                key = { item ->
                    val twoColumn = item
                    // some how lazycolumn super-anally retains the list order by the KEYs!
                    // Since I'm providing sorted list (and sort order ain't maintained)..
                    // let's pass list instance ID, YES, forces full refresh, but at least we're sorted!
                    "$listHash,site=${twoColumn.siteEntry?.id} gpmid=${twoColumn.gpm?.id}"
                }
            ) { twoColumn ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    val siteEntry = twoColumn.siteEntry
                    DraggableText(
                        if (siteEntry != null) DNDObject.SiteEntry(siteEntry) else DNDObject.Spacer,
                        modifier = mySizeModifier.weight(1f),
                        onItemDropped = { (_, maybeId) ->
                            if (siteEntry == null) false
                            else maybeId.toLongOrNull()?.let {
                                CoroutineScope(Dispatchers.IO).launch {
                                    linkSavedGPMAndDecryptableSiteEntry(
                                        siteEntry,
                                        it,
                                        twoColumn.gpm.takeIf { twoColumn.connected },
                                    )
                                }
                                true
                            } ?: false
                        },
                        onTap = {
                            if (siteEntry?.id != null) {
                                IntentManager.startEditPassword(context, siteEntry.id!!)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.fillMaxWidth(0.1f))

                    DraggableText(
                        if (twoColumn.gpm != null) DNDObject.GPM(twoColumn.gpm)
                        else DNDObject.Spacer,
                        modifier = mySizeModifier.weight(1f),
                        onTap = { showInfo.value = twoColumn.gpm }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImportEntryListPreview() {
    SafeTheme {
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val mine = (1990..2023).map {
            DecryptableSiteEntry(1).apply {
                description = encrypter("Description $it".toByteArray())
            }
        }
        val imports = (1..10).map {
            makeFromEncryptedStringFields(
                it.toLong(),
                "name($it)".encrypt(),
                "url".encrypt(),
                "username".encrypt(),
                "password".encrypt(),
                "note".encrypt(),
                false,
                "hash"
            )
        }
        // would require data model mocks to complete
        val fakeViewModel = ImportGPMViewModel().apply {

        }
        AddIgnoreMergeGpmsAndSiteEntriesList(fakeViewModel)
    }
}