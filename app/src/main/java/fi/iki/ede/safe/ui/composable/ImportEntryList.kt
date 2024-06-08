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
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportEntryList(mine: List<String>, imports: List<String>) {
    val maxSize = maxOf(mine.size, imports.size)

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
                    text = if (index < mine.size) mine[index] else null,
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
                    text = if (index < imports.size) imports[index] else null,
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
        val mine = (1990..2023).map { "mine$it" }
        val imports = (1..10).map { "import$it" }

        ImportEntryList(mine, imports = imports)
    }
}