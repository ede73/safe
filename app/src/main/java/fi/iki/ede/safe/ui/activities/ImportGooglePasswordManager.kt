package fi.iki.ede.safe.ui.activities

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity

class ImportGooglePasswordManager : AutolockingBaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val searchText = remember { mutableStateOf(TextFieldValue("")) }
                    Column {
                        ImportControls(searchText)
                        ImportEntryList(
                            listOf("oma1", "oma2"),
                            listOf("tuo1", "tuo2", "tuo3", "tuo4")
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun startMe(context: Context) {
            context.startActivity(Intent(context, ImportGooglePasswordManager::class.java))
        }
    }
}

fun Modifier.visibleSpacer(visible: Boolean, color: Color = Color.Magenta) = this.then(
    if (visible) {
        Modifier
            .background(color)
            .padding(10.dp)
    } else {
        Modifier
    }
)


@Composable
fun ImportEntryList(mine: List<String>, imports: List<String>) {
    val maxSize = maxOf(mine.size, imports.size)
    val mySizeModifier = Modifier
        .fillMaxWidth(0.4f)
        .fillMaxHeight(0.2f)
    LazyColumn {
        itemsIndexed(List(maxSize) { it }) { index, _ ->
            Row {
                DraggableText(
                    text = if (index < mine.size) mine[index] else null,
                    modifier = mySizeModifier,
                    onItemDropped = {
                        println(it)
                    }
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                        .visibleSpacer(true, Color.Yellow)
                )

                DraggableText(
                    text = if (index < imports.size) imports[index] else null,
                    modifier = mySizeModifier,
                    onItemDropped = null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dnd(
    text: String,
    onItemDropped: ((DragAndDropEvent) -> Unit)?,
    dndTarget: DragAndDropTarget
) = this.then(
    if (onItemDropped == null) {
        Modifier.dragAndDropSource {
            detectTapGestures(onLongPress = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableText(
    text: String?,
    modifier: Modifier = Modifier,
    onItemDropped: ((DragAndDropEvent) -> Unit)?
) {
    var tintColor by remember {
        mutableStateOf(Color(0xffE5E4E2))
    }
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                super.onEntered(event)
                tintColor = Color(0xff00ff00)
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEntered(event)
                tintColor = Color(0xffE5E4E2)
            }

            override fun onExited(event: DragAndDropEvent) {
                super.onEntered(event)
                tintColor = Color(0xffE5E4E2)
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
        Spacer(modifier = modifier.visibleSpacer(true))
    } else {
        Box(
            modifier = modifier
                .border(1.dp, tintColor)
                .padding(10.dp)
                .dnd(text, onItemDropped, dndTarget)
        ) {
            Box(modifier = modifier.border(1.dp, Color.Cyan)) {
                Text(text = text)
            }
        }
    }
}

@Composable
fun ImportControls(
//    matchingPasswordEntries: MutableStateFlow<List<DecryptableSiteEntry>>,
    searchTextField: MutableState<TextFieldValue>,
) {
    val searchFromBeingImported = remember { mutableStateOf(false) }
    val searchFromMyOwn = remember { mutableStateOf(false) }

    fun findNow() {

    }

    var hackToInvokeSearchOnlyIfTextValueChanges by remember { mutableStateOf(TextFieldValue("")) }
    Column {
        val iconPadding = Modifier
            .padding(15.dp)
            .size(24.dp)
        TextField(
            value = searchTextField.value,
            onValueChange = { value ->
                searchTextField.value = value
                if (value.text != hackToInvokeSearchOnlyIfTextValueChanges.text) {
                    hackToInvokeSearchOnlyIfTextValueChanges = value
                    //findNow()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTag.TEST_TAG_SEARCH_TEXT_FIELD),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "",
                    modifier = iconPadding
                )
            },
            placeholder = { Text(stringResource(id = R.string.google_password_import_search)) },
            trailingIcon = {
                if (searchTextField.value != TextFieldValue("")) {
                    IconButton(onClick = { searchTextField.value = TextFieldValue("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "",
                            modifier = iconPadding
                        )
                    }
                }
            },
            singleLine = true,
            shape = RectangleShape
        )
        Row {
            TextualCheckbox(
                searchFromMyOwn,
                R.string.google_password_import_search_from_mine,
                ::findNow
            )
            TextualCheckbox(
                searchFromBeingImported,
                R.string.google_password_import_search_from_imports,
                ::findNow
            )
        }
    }
}

@Composable
private fun TextualCheckbox(
    initiallyChecked: MutableState<Boolean>,
    textResourceId: Int,
    startSearch: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = initiallyChecked.value, onCheckedChange = {
            initiallyChecked.value = it
            startSearch()
        })
        Text(text = stringResource(id = textResourceId))
    }
}
