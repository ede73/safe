package fi.iki.ede.safe.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SafeListItem(
    modifier: Modifier = Modifier,
    fillWidthFraction: Float = 1f,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .padding(6.dp)
            .fillMaxWidth(fillWidthFraction),
        shape = MaterialTheme.shapes.medium
    ) {
        content()
    }
}