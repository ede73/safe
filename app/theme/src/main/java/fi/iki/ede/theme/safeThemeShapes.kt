package fi.iki.ede.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
class SafeShapes(
    val button: Shape,
) {
    override fun hashCode(): Int =
        button.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SafeShapes

        return button == other.button
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