package fi.iki.ede.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFe94560),          // Rose pink
    secondary = Color(0xFF34b38a),        // Teal
    tertiary = Color(0xFF8899aa),         // Slate gray
    background = Color(0xFF12121c),       // Deep navy background
    surface = Color(0xFF1e1e2e),          // Card surface background
    surfaceVariant = Color(0xFF1e1e2e),   // List item background
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8899aa), // Subtexts
    outline = Color(0xFF444466),          // Outline borders
    outlineVariant = Color(0xFF444466)    // Matching outline variant
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)


@Composable
fun SafeThemeSurface(
    content: @Composable () -> Unit
) {
    Surface {
        content()
    }
}

@Suppress("DEPRECATION")
@Composable
fun SafeTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val customFonts by remember { mutableStateOf(SafeTheme.customFonts()) }
    val customColors by remember { mutableStateOf(SafeTheme.customColors()) }
    val customShapes by remember { mutableStateOf(SafeTheme.customShapes()) }

    // Dynamic color is available on Android 12+
    val dynamicColor = false
    SafeTheme.colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    BuildDependentSurfaceColor?.let {
        SafeTheme.colorScheme = SafeTheme.colorScheme.copy(onSurface = it)
    }
    // try hard to keep top status bar correct color
    LocalView.current.apply {
        if (!isInEditMode) {
            SideEffect {
                val window = (context as Activity).window
                window.statusBarColor = SafeTheme.colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, this).isAppearanceLightStatusBars =
                    !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = SafeTheme.colorScheme,
        typography = SafeTheme.typography(),
        shapes = SafeTheme.shapes()
    ) {
        CompositionLocalProvider(
            LocalSafeTheme provides SafeThemeData(
                customFonts, customColors, customShapes
            )
        ) {
            content()
        }
    }
}
