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
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun MyProgressDialog(
    showDialog: MutableState<Boolean>,
    message: MutableState<String>,
    text: MutableState<String>
) {
    if (showDialog.value) {
        val messages = remember { mutableStateListOf<String>() }

        LaunchedEffect(text.value) {
            if (messages.size >= 5) {
                messages.removeAt(0)
            }
            messages.add("- ${text.value}")
        }

        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
            },
            dismissButton = {
            },
            title = {
                Text(message.value)
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
        val a = remember { mutableStateOf(true) }
        val b = remember { mutableStateOf("test1") }
        val c = remember { mutableStateOf("test2") }
        MyProgressDialog(a, b, c)
    }
}