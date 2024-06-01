package fi.iki.ede.safe.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)
//internal val LocalColorScheme = staticCompositionLocalOf { lightColorScheme() }
//internal val LocalShapes = staticCompositionLocalOf { Shapes() }

object SafeTheme {
    var colorScheme = darkColorScheme()
}

val LocalSafeFonts = staticCompositionLocalOf { SafeTheme.customFonts() }
val LocalSafeColors = staticCompositionLocalOf { SafeTheme.customColors() }

@Composable
fun SafeTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val customFonts by remember { mutableStateOf(SafeTheme.customFonts()) }
    val customColors by remember { mutableStateOf(SafeTheme.customColors()) }

    // Dynamic color is available on Android 12+
    val dynamicColor = true
    SafeTheme.colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
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
        typography = SafeTheme.typography()
    ) {
        CompositionLocalProvider(LocalSafeFonts provides customFonts) {
            CompositionLocalProvider(LocalSafeColors provides customColors) {
                content()
            }
        }
    }
}
