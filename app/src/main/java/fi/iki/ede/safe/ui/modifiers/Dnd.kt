package fi.iki.ede.safe.ui.modifiers

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dnd(
    text: String,
    onItemDropped: ((DragAndDropEvent) -> Unit)?,
    dndTarget: DragAndDropTarget
) = this.then(
    if (onItemDropped == null) {
        Modifier.dragAndDropSource {
            detectTapGestures(onPress = {
                println("START THE DRAG!")
                startTransfer(
                    DragAndDropTransferData(
                        ClipData.newPlainText("image Url", text),
                        //flags = View.DRAG_FLAG_GLOBAL
                    )
                )
            })
        }
    } else {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                println("shouldStartDragAndDrop $event")
                event
                    .mimeTypes()
                    .contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }, target = dndTarget
        )
    }
)