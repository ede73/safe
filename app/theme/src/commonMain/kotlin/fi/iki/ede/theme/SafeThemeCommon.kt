package fi.iki.ede.theme

import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.material3.lightColorScheme

object SafeTheme {
    var colorScheme = lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )
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
