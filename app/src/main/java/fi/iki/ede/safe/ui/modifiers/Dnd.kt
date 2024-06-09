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
import fi.iki.ede.safe.ui.composable.DNDObject

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dnd(
    dragObject: DNDObject,
    onItemDropped: ((DragAndDropEvent) -> Unit)?,
    dndTarget: DragAndDropTarget
) = this.then(
    if (onItemDropped == null) {
        Modifier.dragAndDropSource {
            detectTapGestures(onPress = {
                startTransfer(
                    DragAndDropTransferData(
                        when (dragObject) {
                            is DNDObject.JustString -> throw Exception("Strings not allowed as source")
                            is DNDObject.GPM -> ClipData.newPlainText(
                                dragObject.savedGPM.decryptedName,
                                dragObject.savedGPM.id.toString()
                            )

                            is DNDObject.SiteEntry -> throw Exception("DecryptableSiteEntry not allowed as source")
                            is DNDObject.Spacer -> throw Exception("Spacers not allowed as source")
                        }
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