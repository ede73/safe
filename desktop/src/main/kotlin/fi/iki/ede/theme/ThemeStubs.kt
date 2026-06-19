package fi.iki.ede.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SafeThemeSurface(content: @Composable () -> Unit) {
    content()
}

@Composable
fun SafeListItem(
    modifier: Modifier = Modifier,
    fillWidthFraction: Float = 1f,
    yOffset: Dp = 0.dp,
    borderColor: BorderStroke? = null,
    color: CardColors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a40)),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .padding(6.dp)
            .offset(y = yOffset)
            .fillMaxWidth(fillWidthFraction),
        colors = color,
        border = borderColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        content()
    }
}

data class SafeThemeData(
    val customFonts: SafeFonts = SafeFonts()
)

val LocalSafeTheme = staticCompositionLocalOf { SafeThemeData() }

class SafeFonts {
    val listHeaders = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFe94560)
    )
}
