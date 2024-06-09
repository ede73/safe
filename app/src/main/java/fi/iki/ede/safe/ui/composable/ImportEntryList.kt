package fi.iki.ede.safe.ui.composable

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.gpm.model.encrypter
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.ui.models.DNDObject
import fi.iki.ede.safe.ui.models.ImportGPMViewModel
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportEntryList(viewModel: ImportGPMViewModel) {
    //mine: List<DecryptableSiteEntry>, imports: List<SavedGPM>) {
    //val maxSize = maxOf(mine.size, imports.size)
    val mine = viewModel.displayedSiteEntries.collectAsState()
    val imports = viewModel.displayedGPMs.collectAsState()

    val maxSize = maxOf(mine.value.size, imports.value.size)
    //println("${mine.value.size}, ${imports.value.size}")
    val context = LocalContext.current

    fun ignoreSavedGPM(id: Long?) {
        if (id == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("ignoreSavedGPM")
                DBHelperFactory.getDBHelper(context).markSavedGPMIgnored(id)
                viewModel.removeGPM(id)
                println("SavedGPM $id ignored and removed from list")
            } catch (ex: Exception) {
                Log.i("ImportEntryList", "ignoreSavedGPM $id failed", ex)
            }
        }
    }

    fun linkSavedGPMAndDecryptableSiteEntry(siteEntry: DecryptableSiteEntry, id: Long?) {
        if (id == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("linkSavedGPMAndDecryptableSiteEntry")
                DBHelperFactory.getDBHelper(context).linkSaveGPMAndSiteEntry(siteEntry.id!!, id)
                println("Link ${siteEntry.id} and SavedGPM $id")
                viewModel.removeGPM(id)
            } catch (ex: Exception) {
                Log.i(
                    "ImportEntryList",
                    "linkSavedGPMAndDecryptableSiteEntry ${siteEntry.id} to $id failed",
                    ex
                )
            }
        }
    }

    fun addSavedGPM(id: Long?) {
        if (id == null) return
        println("Add(import) GPM $id")
        viewModel.removeGPM(id)
    }
    Row {
        DraggableText(
            DNDObject.JustString("Add"),
            onItemDropped = { event ->
                if (event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    addSavedGPM(
                        event.toAndroidDragEvent()
                            .clipData.getItemAt(0).text.toString().toLongOrNull()
                    )
                }
            }
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.5f)
            //.visibleSpacer(true, Color.Yellow)
        )

        DraggableText(
            DNDObject.JustString("Ignore"),
            onItemDropped = { event ->
                if (event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    ignoreSavedGPM(
                        event.toAndroidDragEvent()
                            .clipData.getItemAt(0).text.toString().toLongOrNull()
                    )
                }
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
                //println("l-m ${relativeDragY}")
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
        state = listState, modifier = Modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    true
                }, target = dndTarget
            )
            .onGloballyPositioned { coordinates ->
                lazyColumnTopY = coordinates.positionInWindow().y
            }
    ) {
        itemsIndexed(List(maxSize) { it }) { index, _ ->
            Row {
                DraggableText(
                    if (index < mine.value.size)
                        DNDObject.SiteEntry(mine.value[index])
                    else DNDObject.Spacer,
                    modifier = mySizeModifier.weight(1f),
                    onItemDropped = { event ->
                        if (event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                            linkSavedGPMAndDecryptableSiteEntry(
                                mine.value[index],
                                event.toAndroidDragEvent()
                                    .clipData.getItemAt(0).text.toString().toLongOrNull()
                            )
                        }
                    }
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                    // .visibleSpacer(true, Color.Yellow)
                )

                DraggableText(
                    if (index < imports.value.size)
                        DNDObject.GPM(imports.value[index])
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