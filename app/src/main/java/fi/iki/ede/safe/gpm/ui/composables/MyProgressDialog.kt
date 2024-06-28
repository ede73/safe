package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.ui.theme.SafeButton
import kotlinx.coroutines.flow.MutableStateFlow

object ProgressStateHolder {
    val progressMessageFlow = MutableStateFlow<List<String>>(emptyList())

    // make sure you are in main thread (withContect)
    fun addMessage(message: String) {
        progressMessageFlow.value = (progressMessageFlow.value + message).takeLast(7)
    }
}

@Composable
fun MyProgressDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    showCloseButton: MutableState<Boolean>
) {
    if (showDialog.value) {
        val progressMessageFlow = ProgressStateHolder.progressMessageFlow
        val messages by remember { progressMessageFlow }.collectAsState()

        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
            },
            dismissButton = {
                if (showCloseButton.value) {
                    SafeButton(onClick = {
                        showDialog.value = false
                    }) {
                        Text("Close")
                    }
                }
            },
            title = {
                Text(title)
            },
            text = {
                Column(modifier = Modifier.padding(16.dp)) {
                    messages.forEach { message: String ->
                        Text(text = message)
                    }
                }
            }
        )
    }
}

//@Preview(showBackground = true)
//@Composable
//fun MyProgressDialogPreview() {
//    SafeTheme {
//        val showDialog = remember { mutableStateOf(true) }
//        val showClose = remember { mutableStateOf(true) }
//        MyProgressDialog(
//            showDialog,
//            "title",
//            ArrayDeque<String>().apply {
//                add("x")
//                add("Y")
//            },
//            showClose
//        )
//    }
//}