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
            if (linkedSavedGPM != null) {
                viewModel.removeConnectedDisplayItem(siteEntry, linkedSavedGPM)
            } else {
                viewModel.removeAllMatchingGpmsFromDisplayAndUnprocessedLists(gpmId)
            }
            // TODO: should remove the MATCHING SiteEntry too - IF we're doing matching lists
            DataModel.linkSaveGPMAndSiteEntry(siteEntry, gpmId)
        } catch (ex: Exception) {
            firebaseRecordException(
                "linkSavedGPMAndDecryptableSiteEntry ${siteEntry.id} to $gpmId failed",
                ex
            )
        }
    }

    fun addSavedGPM(savedGPMId: Long, maybeLinkedSiteEntry: Pair<DecryptableSiteEntry, SavedGPM>?) {
        // TODO: Adding to first category...
        // either make import category automatically or ASK user whe
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
        // TODO: combine the lists, other has sorting, this one below doesn't
        true -> connectedDisplayItems.value.map { SiteEntryToGPM(it.first, it.second, true) }
        false -> combineLists(
            displayedSiteEntries.value,
            displayedGPMs.value,
            viewModel.displayedItemsAreConnected // REMOVE
        )

    }
    val s = combinedList.map { it.siteEntry }.toSet()
    val g = combinedList.map { it.gpm }.toSet()
    val listHash = "${s.hashCode()}-${g.hashCode()}-${combinedList.hashCode()}"

//    val itemPositions = remember(combinedList) { mutableStateMapOf<String, Pair<Rect, Rect>>() }

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
                    val site = item
                    // some how lazycolumn super-anally retains the list order by the KEYs!
                    // Since I'm providing sorted list (and sort order ain't maintained)..
                    // let's pass list instance ID, YES, forces full refresh, but at least we're sorted!
                    "$listHash,site=${site.siteEntry?.id} gpmid=${site.gpm?.id}"
                }
            ) { x ->
                val site = x
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
//                        .onGloballyPositioned { coordinates ->
//                            val position = coordinates.positionInParent()
//                            val key = "${site.siteEntry?.id}-${site.gpm?.id}"
//                            if (site.gpm != null) {
//                                parentPositions[key] = Pair(position.y, position.y)
//                            }
//                        }
                ) {
//                    val siteEntryCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
//                    val gpmCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val siteEntry = site.siteEntry
                    DraggableText(
                        if (siteEntry != null) DNDObject.SiteEntry(siteEntry) else DNDObject.Spacer,
                        modifier = mySizeModifier
                            .weight(1f),
//                            .onGloballyPositioned { siteEntryCoordinates.value = it },
                        onItemDropped = { (_, maybeId) ->
                            if (siteEntry == null) false
                            else maybeId.toLongOrNull()?.let {
                                CoroutineScope(Dispatchers.IO).launch {
                                    linkSavedGPMAndDecryptableSiteEntry(
                                        siteEntry,
                                        it,
                                        site.gpm.takeIf { site.connected },
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
                        if (site.gpm != null) DNDObject.GPM(site.gpm)
                        else DNDObject.Spacer,
                        modifier = mySizeModifier
                            .weight(1f),
//                            .onGloballyPositioned { gpmCoordinates.value = it },
                        onTap = { showInfo.value = site.gpm }
                    )
//                    LaunchedEffect(siteEntryCoordinates.value, gpmCoordinates.value) {
//                        val siteEntryCoord = siteEntryCoordinates.value
//                        val gpmCoord = gpmCoordinates.value
//                        val rect = makeRect(siteEntryCoord!!) to makeRect(gpmCoord!!)
//                        if (rect.first != null && rect.second != null) {
//                            val hash = "${site.siteEntry?.id}-${site.gpm?.id}"
//                            itemPositions[hash] = rect as Pair<Rect, Rect>
////                            println("${parentPositions[hash]}==${rect}")
//                        }
//                    }
                }
            }
        }
//        if (viewModel.displayedItemsAreConnected) {
//            // DrawConnectingLines(itemPositions, Modifier.matchParentSize())
//        }
    }
}

//private fun makeRect(
//    layoutCoord: LayoutCoordinates
//) = layoutCoord.parentLayoutCoordinates?.let {
//    Rect(
//        layoutCoord.positionInWindow().x,
//        it.positionInParent().y,
//        layoutCoord.positionInWindow().x + layoutCoord.size.width.toFloat(),
//        it.positionInParent().y + layoutCoord.size.height.toFloat()
//    )
//}

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

//@Composable
//fun DrawConnectingLines(
//    itemPositions: SnapshotStateMap<String, Pair<Rect, Rect>>,
//    modifier: Modifier
//) {
//    Canvas(modifier = modifier) {
//        val lineColor = Color.Black.copy(alpha = 0.4f)
//        val lineWidth = 2.dp.toPx()
//
//        itemPositions.forEach { (_, positions) ->
//            val (left, right) = positions
//            drawLine(
//                color = lineColor,
//                start = Offset(left.right, left.top + (left.bottom - left.top) / 2),
//                end = Offset(right.left, right.top + (left.bottom - left.top) / 2),
//                strokeWidth = lineWidth,
//                cap = Stroke.DefaultCap
//            )
//        }
//    }
//}