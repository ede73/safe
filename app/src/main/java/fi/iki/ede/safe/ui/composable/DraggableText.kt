package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.ui.modifiers.dnd

@Composable
fun DraggableText(
    text: String?,
    modifier: Modifier = Modifier,
    onItemDropped: ((DragAndDropEvent) -> Unit)? = null
) {
    val defaultColor = Color.Unspecified
    var dndHighlight by remember { mutableStateOf(defaultColor) }
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                println("e ${event.toAndroidDragEvent().y}")
                dndHighlight = Color(0, 255, 255, 50)
            }

            override fun onMoved(event: DragAndDropEvent) {
                super.onEntered(event)
                println("m ${event.toAndroidDragEvent().y}")
                dndHighlight = Color(0, 255, 255, 50)
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEntered(event)
                dndHighlight = defaultColor
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onEntered(event)
                dndHighlight = defaultColor
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                println("onDrop $event")
                val draggedData = event.toAndroidDragEvent()
                    .clipData.getItemAt(0).text
                println("Got $draggedData")
                // Parse received data
                onItemDropped!!(event)
                return true
            }
        }
    }

    if (text == null) {
        Spacer(modifier = modifier)
    } else {
        Box(
            modifier = modifier
                .border(1.dp, if (onItemDropped == null) Color.Red else Color.Green)
                .background(dndHighlight)
                .padding(10.dp)
                .dnd(text, onItemDropped, dndTarget)
        ) {
            Box {
                Text(text = text)
            }
        }
    }
}