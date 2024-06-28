package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.delay

@Composable
fun MyProgressDialog(
    showDialog: MutableState<Boolean>,
    title: String,
    messageQueue: ArrayDeque<String>,
    showCloseButton: MutableState<Boolean>
) {
    if (showDialog.value) {
        val messages = remember { mutableStateListOf<String>() }

        LaunchedEffect(messageQueue) {
            // Consume messages from the queue
            while (!showCloseButton.value) {
                if (messageQueue.isNotEmpty()) {
                    val message = messageQueue.removeFirst()
                    if (messages.size >= 5) {
                        messages.removeAt(0)
                    }
                    messages.add("- $message")
                }
                delay(1000) // Delay to allow the UI to catch up
            }
        }

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
                    messages.forEach { message ->
                        Text(text = message)
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MyProgressDialogPreview() {
    SafeTheme {
        val showDialog = remember { mutableStateOf(true) }
        val showClose = remember { mutableStateOf(true) }
        MyProgressDialog(
            showDialog,
            "title",
            ArrayDeque<String>().apply {
                add("x")
                add("Y")
            },
            showClose
        )
    }
}