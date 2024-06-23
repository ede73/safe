package fi.iki.ede.safe.gpm.ui.composables

import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.gpm.ui.models.DNDObject
import fi.iki.ede.safe.gpm.ui.modifiers.dnd
import fi.iki.ede.safe.gpm.ui.modifiers.getClipData
import fi.iki.ede.safe.ui.theme.SafeTheme

fun DNDObject.dump(): String =
    "DNDObject:" +
            when (this) {
                is DNDObject.JustString -> this.string
                is DNDObject.GPM -> "${this.savedGPM.cachedDecryptedName} - ${this.savedGPM.id}"
                is DNDObject.SiteEntry -> "${this.decryptableSiteEntry.cachedPlainDescription} - ${this.decryptableSiteEntry.id}"
                is DNDObject.Spacer -> "Spacer"
            }

@Composable
fun DraggableText(
    dragObject: DNDObject,
    modifier: Modifier = Modifier,
    onItemDropped: ((Pair<ClipDescription, String>) -> Boolean)? = null
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
                    .border(1.dp, if (onItemDropped == null) Color.Red else Color.Green)
                    .background(dndHighlight)
                    .padding(10.dp)
                    .checkIfVisible(isVisible)
                    .let {
                        if (isVisible.value) {
                            //println("Add a DND Target ${dragObject.dump()}..we are visible")
                            // works but doesnt act as DND targe
                            it.dnd(dragObject, onItemDropped, dndTarget)
                            //it.dndTargetThatWorksPerfectly(dragObject, dndTarget, isVisible.value)
                            // literally DOES NOT work
                            //it.dnd(dragObject, false, dndTarget)
                        } else {
                            //println("Skip a DND Target${dragObject.dump()}..we are NOT visible")
                            it
                        }
                    }
            ) {
                Box {
                    when (dragObject) {
                        is DNDObject.JustString -> Text(text = dragObject.string)
                        is DNDObject.GPM -> Text(text = dragObject.savedGPM.cachedDecryptedName)
                        is DNDObject.SiteEntry -> Text(text = dragObject.decryptableSiteEntry.cachedPlainDescription)
                        is DNDObject.Spacer -> throw Exception("No spaces allowed")
                    }
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
fun DraggableTextPreview() {
    SafeTheme {
        DraggableText(DNDObject.JustString("Hello"), onItemDropped = {
            println("item dropped")
            false
        })
    }
}