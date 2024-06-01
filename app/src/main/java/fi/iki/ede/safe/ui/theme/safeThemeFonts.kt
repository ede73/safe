package fi.iki.ede.safe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
class SafeFonts(
    val regularPassword: TextStyle,
    val zoomedPassword: TextStyle,
    val listHeaders: TextStyle,
    val listEntries: TextStyle,
    val datePicker: TextStyle,
    val smallNote: TextStyle
) {
    override fun hashCode(): Int {
        var result = regularPassword.hashCode()
        result = 31 * result + zoomedPassword.hashCode()
        result = 31 * result + zoomedPassword.hashCode()
        result = 31 * result + listHeaders.hashCode()
        result = 31 * result + listEntries.hashCode()
        result = 31 * result + datePicker.hashCode()
        result = 31 * result + smallNote.hashCode()
        return result
    }
}

internal fun SafeTheme.customFonts() = SafeFonts(
    regularPassword = TextStyle(
        fontFamily = FontFamily.Default,
        letterSpacing = 1.sp,
        fontSize = 25.sp,
    ), zoomedPassword = TextStyle(
        fontFamily = FontFamily.Default,
        letterSpacing = 2.sp,
        fontSize = 25.sp,
    ), listHeaders = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp
    ), listEntries = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
    ), datePicker = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ), smallNote = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp
    )
)

// Set of Material typography styles to start with
internal fun SafeTheme.typography() = Typography(
    // regular text(Text())
    // also text labels of TextFields when field is empty
    // Dialog texts
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp
    ),
    // Use at site entry list headers
    headlineMedium = customFonts().listHeaders,
    // Used at buttons (Copy, Breach check, Take a photo etc.)
    // also the POP UP menu...
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp,
    ),
    // The window title!
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 25.sp,
    ),
)
