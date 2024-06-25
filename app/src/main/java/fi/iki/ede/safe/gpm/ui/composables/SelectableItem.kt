package fi.iki.ede.safe.gpm.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.safe.ui.theme.SafeTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableItem(text: String, showInfo: () -> Unit, leftSpacer: Boolean = false) {
    var isSelected by remember { mutableStateOf(false) }
    Row(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { isSelected = !isSelected },
            onLongClick = { showInfo() }
        )
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Checkmark")
        }
        if (leftSpacer) {
            Spacer(modifier = Modifier.weight(0.5f))
        }
        Text(
            text = text,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SelectableItemPreview() {
    SafeTheme {
        SelectableItem("item", {},true)
    }
}