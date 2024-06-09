package fi.iki.ede.safe.ui.composable

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
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.DecryptableSiteEntry
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.gpm.model.SavedGPM.Companion.makeFromEncryptedStringFields
import fi.iki.ede.gpm.model.encrypt
import fi.iki.ede.gpm.model.encrypter
import fi.iki.ede.safe.ui.activities.ImportGPMViewModel
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportEntryList(viewModel: ImportGPMViewModel) {
    //mine: List<DecryptableSiteEntry>, imports: List<SavedGPM>) {
    //val maxSize = maxOf(mine.size, imports.size)
    val mine = viewModel.displayedSiteEntries.collectAsState()
    val imports = viewModel.displayedGPMs.collectAsState()

    val maxSize = maxOf(mine.value.size, imports.value.size)
    println("${mine.value.size}, ${imports.value.size}")

    Row {
        DraggableText(
            text = "Add",
            onItemDropped = {
                println("Add:$it")
            }
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.5f)
            //.visibleSpacer(true, Color.Yellow)
        )

        DraggableText(
            text = "Ignore",
            onItemDropped = {
                println("IGNORE:$it")
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
                println("l-m ${relativeDragY}")
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
                    text = if (index < mine.value.size) mine.value[index].plainDescription else null,
                    modifier = mySizeModifier.weight(1f),
                    onItemDropped = {
                        println("LINK $it")
                    }
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                    // .visibleSpacer(true, Color.Yellow)
                )

                DraggableText(
                    text = if (index < imports.value.size) imports.value[index].decryptedName else null,
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