package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import fi.iki.ede.safe.gpm.ui.modifiers.doesItHaveText
import fi.iki.ede.safe.gpm.ui.utilities.CombinedListPairs
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportEntryList(viewModel: ImportGPMViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showInfo = remember { mutableStateOf<SavedGPM?>(null) }
    if (showInfo.value != null) {
        ShowInfoDialog(showInfo.value!!, onDismiss = { showInfo.value = null })
    }

    fun ignoreSavedGPM(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: who sets flagged ignore here!
                DataModel.markSavedGPMIgnored(id)
                // TODO: should autolink(from datamodel)
                viewModel.removeGPMFromMergeRepository(id)
            } catch (ex: Exception) {
                firebaseRecordException("ignoreSavedGPM $id failed", ex)
            }
        }
    }

    fun linkSavedGPMAndDecryptableSiteEntry(siteEntry: DecryptableSiteEntry, id: Long) {
        try {
            viewModel.removeGPMFromMergeRepository(id)
            DataModel.linkSaveGPMAndSiteEntry(siteEntry, id)
        } catch (ex: Exception) {
            firebaseRecordException(
                "linkSavedGPMAndDecryptableSiteEntry ${siteEntry.id} to $id failed",
                ex
            )
        }
    }

    fun addSavedGPM(savedGPMId: Long) {
        // TODO: Adding to first category...
        // either make import category automatically or ASK user whe
        val catId =
            DataModel.getCategories().firstOrNull { it.plainName == "Google Password Manager" }

        suspend fun addNow(catId: DBID) {
            DataModel.addGpmAsSiteEntry(savedGPMId, categoryId = catId, onAdd = {
                linkSavedGPMAndDecryptableSiteEntry(it, savedGPMId)
            })
        }

        coroutineScope.launch(Dispatchers.IO) {
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

    Row {
        DraggableText(
            DNDObject.JustString("Add"),
            onItemDropped = { (_, maybeId) ->
                maybeId.toLongOrNull()?.let {
                    addSavedGPM(it)
                    true
                } ?: false
            }
        )

        SafeButton(onClick = {
            coroutineScope.launch {
                viewModel.importMergeDataRepository.resetSiteEntryDisplayListToAllSaved()
            }
        }) {
            Text(stringResource(R.string.google_password_import_merge_reset_password_list))
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.2f)
            //.visibleSpacer(true, Color.Yellow)
        )

        SafeButton(
            modifier = Modifier.padding(0.dp),
            onClick = {
                coroutineScope.launch {
                    viewModel.importMergeDataRepository.resetGPMDisplayListToAllUnprocessed()
                }
            }) {
            Text(
                stringResource(R.string.google_password_import_merge_reset_gpm_list),
                modifier = Modifier.padding(0.dp),
            )
        }
        DraggableText(
            DNDObject.JustString("Ignore"),
            onItemDropped = { (_, maybeId) ->
                maybeId.toLongOrNull()?.let {
                    ignoreSavedGPM(it)
                    true
                } ?: false
            }
        )
    }
    var lazyColumnTopY by remember { mutableStateOf(0f) }

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

    val imports = viewModel.importMergeDataRepository.displayedUnprocessedGPMs.collectAsState()
    val mine = viewModel.importMergeDataRepository.displayedSiteEntries.collectAsState()
    val combinedList = mutableListOf<CombinedListPairs>().apply {
        addAll(
            combineLists(
                mine.value,
                imports.value
            )
        )
    }.toList()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    doesItHaveText(event)
                }, target = dndTarget
            )
            .onGloballyPositioned { coordinates ->
                lazyColumnTopY = coordinates.positionInWindow().y
            }
    ) {
        items(
            combinedList,
            key = { item ->
                val site = item as CombinedListPairs.SiteEntryToGPM
                // some how lazycolumn super-anally retains the list order by the KEYs!
                // Since I'm providing sorted list (and sort order ain't maintained)..
                // let's pass list instance ID, YES, forces full refresh, but at least we're sorted!
                "${combinedList.hashCode()},site=${site.siteEntry?.id} gpmid=${site.gpm?.id}"
            }
        ) { x ->
            val site = x as CombinedListPairs.SiteEntryToGPM
            Row {
                val siteEntry = site.siteEntry
                DraggableText(
                    if (siteEntry != null) DNDObject.SiteEntry(siteEntry) else DNDObject.Spacer,
                    modifier = mySizeModifier.weight(1f),
                    onItemDropped = { (_, maybeId) ->
                        if (siteEntry == null) false
                        else maybeId.toLongOrNull()?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                linkSavedGPMAndDecryptableSiteEntry(siteEntry, it)
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
                    if (site.gpm != null)
                        DNDObject.GPM(site.gpm)
                    else DNDObject.Spacer,
                    modifier = mySizeModifier.weight(1f),
                    onTap = { showInfo.value = site.gpm }
                )
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
        // would require datamodel mocks to complete
        val fakeViewModel = ImportGPMViewModel().apply {

        }
        ImportEntryList(fakeViewModel)
    }
}