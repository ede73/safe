package fi.iki.ede.gpmui.composables

import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.gpmui.BuildConfig
import fi.iki.ede.gpmui.models.DNDObject
import fi.iki.ede.gpmui.modifiers.dnd
import fi.iki.ede.gpmui.modifiers.getClipData
import fi.iki.ede.logger.Logger

private const val TAG = "DraggableText"

@Composable
fun DraggableText(
    dragObject: DNDObject,
    modifier: Modifier = Modifier,
    onItemDropped: ((Pair<ClipDescription, String>) -> Boolean)? = null,
    onTap: (Offset) -> Unit = {}
) {
    val defaultColor = Color.Unspecified
    var dndHighlight by remember { mutableStateOf(defaultColor) }
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                dndHighlight = Color(0, 255, 255, 50)
            }

            override fun onMoved(event: DragAndDropEvent) {
                super.onEntered(event)
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

            // delegate onwards
            override fun onDrop(event: DragAndDropEvent) =
                getClipData(event)?.let {
                    onItemDropped?.invoke(it)
                } ?: false
        }
    }

    val isVisible = remember { mutableStateOf(false) }

    // Modifier to update visibility
    fun Modifier.checkIfVisible(isVisible: MutableState<Boolean>): Modifier =
        this.onGloballyPositioned { layoutCoordinates ->
            val positionInParent = layoutCoordinates.positionInParent()
            isVisible.value = positionInParent.x >= 0 && positionInParent.y >= 0
        }

    when (dragObject) {
        is DNDObject.Spacer -> Spacer(modifier = modifier)
        else ->
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .fillMaxWidth()
                    .border(
                        4.dp,
                        if (onItemDropped == null) Color.Red.copy(alpha = 0.2f) else Color.Green.copy(
                            alpha = 0.2f
                        )
                    )
                    .background(dndHighlight)
                    .padding(vertical = 2.dp)
                    .checkIfVisible(isVisible)
                    .let {
                        if (isVisible.value) {
                            it
                                .dnd(
                                    dragObject,
                                    onItemDropped,
                                    dndTarget
                                )
                                .let { dnd ->
                                    if (dragObject is DNDObject.SiteEntry) {
                                        dnd.clickable {
                                            onTap(Offset(0f, 0f))
                                        }
                                    } else dnd
                                }
                        } else {
                            it
                        }
                    }) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .align(Alignment.Center)
                ) {
                    when (dragObject) {
                        is DNDObject.JustString -> Text(text = dragObject.string)
                        is DNDObject.GPM -> Text(text = dragObject.savedGPM.cachedDecryptedName.let {
                            if (BuildConfig.DEBUG)
                                it + "(${dragObject.savedGPM.id})}"
                            else it
                        })

                        is DNDObject.SiteEntry -> Text(text = dragObject.decryptableSiteEntry.cachedPlainDescription.let {
                            if (BuildConfig.DEBUG)
                                it + "(${dragObject.decryptableSiteEntry.id})"
                            else
                                it
                        })

                        is DNDObject.Spacer -> throw Exception("No spaces allowed")
                    }
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
fun DraggableTextPreview() {
    MaterialTheme {
        DraggableText(DNDObject.JustString("Hello"), onItemDropped = {
            Logger.d(TAG, "item dropped")
            false
        })
    }
}