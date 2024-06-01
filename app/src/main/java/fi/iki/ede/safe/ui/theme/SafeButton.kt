package fi.iki.ede.safe.ui.theme

import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SafeButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val safeTheme = LocalSafeTheme.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = safeTheme.customShapes.button
    ) {
        content()
    }
}