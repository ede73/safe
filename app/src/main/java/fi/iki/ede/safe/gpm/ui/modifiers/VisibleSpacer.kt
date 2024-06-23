package fi.iki.ede.safe.gpm.ui.modifiers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.visibleSpacer(visible: Boolean, color: Color = Color.Magenta) = this.then(
    if (visible) {
        Modifier
            .background(color)
            .padding(10.dp)
    } else {
        Modifier
    }
)