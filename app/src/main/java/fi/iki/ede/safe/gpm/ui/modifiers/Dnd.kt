package fi.iki.ede.safe.gpm.ui.modifiers

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
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import fi.iki.ede.safe.gpm.ui.models.DNDObject

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dnd(
    dragObject: DNDObject,
    onItemDropped: ((Pair<ClipDescription, String>) -> Boolean)?,
    dndTarget: DragAndDropTarget,
    onTap: (Offset) -> Unit = {}
) = this.then(
    if (onItemDropped == null) {
        Modifier.dragAndDropSource {
            detectTapGestures(onLongPress = {
                startTransfer(
                    DragAndDropTransferData(
                        when (dragObject) {
                            is DNDObject.JustString -> throw Exception("Strings not allowed as source")
                            is DNDObject.GPM -> setClipData(dragObject)
                            is DNDObject.SiteEntry -> throw Exception("DecryptableSiteEntry not allowed as source")
                            is DNDObject.Spacer -> throw Exception("Spacers not allowed as source")
                        }
                        //flags = View.DRAG_FLAG_GLOBAL
                    )
                )
            }, onTap = onTap)
        }
    } else {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                doesItHaveText(event)
            }, target = dndTarget
        )
    }
)

fun setClipData(dragObject: DNDObject.GPM): ClipData {
    return ClipData.newPlainText(
        dragObject.savedGPM.cachedDecryptedName,
        dragObject.savedGPM.id.toString()
    )
}

fun doesItHaveText(event: DragAndDropEvent) =
    event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)

fun getClipData(event: DragAndDropEvent): Pair<ClipDescription, String>? =
    doesItHaveText(event).let {
        getDraggedClipData(event)?.let { data ->
            val item = data.getItemAt(0)
            data.description to item.text.toString()
        }
    }

fun getDraggedClipData(event: DragAndDropEvent): ClipData? =
    event.toAndroidDragEvent().clipData?.let {
        if (it.itemCount > 0) {
            it
        } else {
            null
        }
    }