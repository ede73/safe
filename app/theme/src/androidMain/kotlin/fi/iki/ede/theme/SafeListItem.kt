package fi.iki.ede.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SafeListItem(
    modifier: Modifier = Modifier,
    fillWidthFraction: Float = 1f,
    yOffset: Dp = 0.dp,
    borderColor: BorderStroke? = null,
    color: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .padding(6.dp)
            .offset(y = yOffset)
            .fillMaxWidth(fillWidthFraction),
        colors = color,
        border = borderColor,
        shape = MaterialTheme.shapes.medium
    ) {
        content()
    }
}