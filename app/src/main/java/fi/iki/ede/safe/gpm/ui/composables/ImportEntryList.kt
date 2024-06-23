package fi.iki.ede.safe.gpm.ui.composables

import android.content.ClipDescription
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.gpm.model.encrypter
import fi.iki.ede.safe.gpm.ui.models.DNDObject
import fi.iki.ede.safe.gpm.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.gpm.ui.modifiers.doesItHaveText
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class CombinedListPairs {
    data class SiteEntryToGPM(val siteEntry: DecryptableSiteEntry?, val gpm: SavedGPM?) :
        CombinedListPairs()
}

fun combineLists(
    siteEntries: List<DecryptableSiteEntry>,
    gpms: List<SavedGPM>
): List<CombinedListPairs> {
    val maxSize = maxOf(siteEntries.size, gpms.size)
    val combinedList = mutableListOf<CombinedListPairs>()

    for (i in 0 until maxSize) {
        combinedList.add(
            CombinedListPairs.SiteEntryToGPM(
                siteEntries.getOrNull(i),
                gpms.getOrNull(i)
            )
        )
    }

    return combinedList
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportEntryList(viewModel: ImportGPMViewModel) {
    val mine = viewModel.importMergeDataRepository.displayedSiteEntries.collectAsState()
    val imports = viewModel.importMergeDataRepository.displayedGPMs.collectAsState()
    val combinedList = combineLists(mine.value, imports.value)

    fun ignoreSavedGPM(clipDescription: ClipDescription, id: Long) {
        println("ignoreSavedGPM ${clipDescription.label} $id")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: who sets flagged ignore here!
                DataModel.markSavedGPMIgnored(id)
                // TODO: should autolink(from datamodel)
                viewModel.removeGPM(id)
                println("SavedGPM $id ignored and removed from list")
            } catch (ex: Exception) {
                Log.i("ImportEntryList", "ignoreSavedGPM $id failed", ex)
            }
        }
    }

    fun linkSavedGPMAndDecryptableSiteEntry(
        clipDescription: ClipDescription,
        siteEntry: DecryptableSiteEntry,
        id: Long
    ) {
        println("LINK GPM AND ENTRY--> clip=(${clipDescription.label})  ==TO site=(${siteEntry.plainDescription}) gpmid=$id")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: can link these two soon
                viewModel.removeGPM(id)
                DataModel.linkSaveGPMAndSiteEntry(siteEntry, id)
                println("Link ${siteEntry.id} / ${siteEntry.plainDescription} and SavedGPM $id")
            } catch (ex: Exception) {
                Log.i(
                    "ImportEntryList",
                    "linkSavedGPMAndDecryptableSiteEntry ${siteEntry.id} to $id failed",
                    ex
                )
            }
        }
    }

    fun addSavedGPM(clipDescription: ClipDescription, id: Long) {
        println("Add(import) GPM ${clipDescription.label} $id")
        viewModel.removeGPM(id)
    }

    Row {
        DraggableText(
            DNDObject.JustString("Add"),
            onItemDropped = { (clipDescription, maybeId) ->
                maybeId.toLongOrNull()?.let {
                    addSavedGPM(clipDescription, it)
                    true
                } ?: false
            }
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.5f)
            //.visibleSpacer(true, Color.Yellow)
        )

        DraggableText(
            DNDObject.JustString("Ignore"),
            onItemDropped = { (clipDescription, maybeId) ->
                maybeId.toLongOrNull()?.let {
                    ignoreSavedGPM(clipDescription, it)
                    true
                } ?: false
            }
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var lazyColumnTopY by remember { mutableStateOf(0f) }

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

    val mySizeModifier = Modifier
        .fillMaxWidth(0.4f)
        .fillMaxHeight(0.2f)
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
                "site=${site.siteEntry?.id} gpmid=${site.gpm?.id}"
            }
        ) { x ->
            val site = x as CombinedListPairs.SiteEntryToGPM
            Row {
                val siteEntry = site.siteEntry
                DraggableText(
                    if (siteEntry != null) DNDObject.SiteEntry(siteEntry) else DNDObject.Spacer,
                    modifier = mySizeModifier.weight(1f),
                    onItemDropped = { (clipDescription, maybeId) ->
                        if (siteEntry == null) false
                        else maybeId.toLongOrNull()?.let {
                            linkSavedGPMAndDecryptableSiteEntry(clipDescription, siteEntry, it)
                            true
                        } ?: false
                    }
                )

                Spacer(modifier = Modifier.fillMaxWidth(0.1f))

                DraggableText(
                    if (site.gpm != null)
                        DNDObject.GPM(site.gpm)
                    else DNDObject.Spacer,
                    modifier = mySizeModifier.weight(1f),
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
        //TODO:   ImportEntryList(mine, imports)
    }
}