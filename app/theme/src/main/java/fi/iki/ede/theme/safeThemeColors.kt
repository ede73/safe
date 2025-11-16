package fi.iki.ede.theme

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SafeColors

        if (numbers108652 != other.numbers108652) return false
        if (lettersL != other.lettersL) return false
        if (whiteSpaceL != other.whiteSpaceL) return false

        return true
    }
}

internal fun SafeTheme.customColors() = SafeColors(
    numbers108652 = Color.Blue.copy(alpha = 0.7f),
    lettersL = Color.Red.copy(alpha = 0.7f),
    whiteSpaceL = Color.Yellow.copy(alpha = 0.7f),
)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val BuildDependentSurfaceColor = if (BuildConfig.BUILD_TYPE == "instrumentationTest") Color.Yellow
else if (BuildConfig.DEBUG) Color.Red else null
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)