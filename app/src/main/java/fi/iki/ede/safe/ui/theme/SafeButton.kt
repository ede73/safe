package fi.iki.ede.safe.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SafeButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentPadding: PaddingValues? = null,
    content: @Composable () -> Unit
) {
    val safeTheme = LocalSafeTheme.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = safeTheme.customShapes.button,
        contentPadding = contentPadding ?: ButtonDefaults.ContentPadding
    ) {
        content()
    }
}