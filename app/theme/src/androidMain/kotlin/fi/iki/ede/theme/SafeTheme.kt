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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

object SafeTheme {
    var colorScheme = LightColorScheme
}

data class SafeThemeData(
    val customFonts: SafeFonts,
    val customColors: SafeColors,
    val customShapes: SafeShapes
)

val LocalSafeTheme =
    staticCompositionLocalOf {
        SafeThemeData(
            SafeTheme.customFonts(),
            SafeTheme.customColors(),
            SafeTheme.customShapes()
        )
    }


@Composable
fun SafeThemeSurface(
    content: @Composable () -> Unit
) {
    Surface {
        content()
    }
}

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
                window.statusBarColor = SafeTheme.colorScheme.primary.toArgb()
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
