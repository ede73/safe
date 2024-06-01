package fi.iki.ede.safe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
class SafeShapes(
    val button: Shape,
) {
    override fun hashCode(): Int {
        var result = button.hashCode()
        return result
    }
}

internal fun SafeTheme.customShapes() = SafeShapes(
    button = RoundedCornerShape(10.dp)
)

internal fun SafeTheme.shapes() = Shapes(
    // Cards
    medium = RoundedCornerShape(20.dp),
    // Textboxes
    extraSmall = RoundedCornerShape(20.dp),
)