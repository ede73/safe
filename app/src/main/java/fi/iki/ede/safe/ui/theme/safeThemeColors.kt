package fi.iki.ede.safe.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
class SafeColors(
    val numbers108652: Color,
    val lettersL: Color,
    val whiteSpaceL: Color,
) {
    override fun hashCode(): Int {
        var result = numbers108652.hashCode()
        result = 31 * result + lettersL.hashCode()
        result = 31 * result + whiteSpaceL.hashCode()
        return result
    }
}

internal fun SafeTheme.customColors() = SafeColors(
    numbers108652 = Color.Blue.copy(alpha = 0.7f),
    lettersL = Color.Red.copy(alpha = 0.7f),
    whiteSpaceL = Color.Yellow.copy(alpha = 0.7f)
)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)