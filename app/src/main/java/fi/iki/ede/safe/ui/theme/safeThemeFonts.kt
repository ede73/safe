package fi.iki.ede.safe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
fun getFonts() = Typography(
    // Use as debug tool
    //background = Color.Yellow,

    // regular text(Text())
    // also text labels of TextFields when field is empty
    // Dialog texts
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 19.sp
    ),
    // used at:
    // category list -> category count
    // site entry list -> site entry age
    // matching search results -> category and age
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp
    ),
    // Use at site entry list headers
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp
    ),
    // Used at buttons (Copy, Breach check, Take a photo etc.)
    // also the POP UP menu...
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp
    ),
    // The window title!
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 25.sp
    ),
    // Text labels in SiteEntry edit (view) screen at the top of the TextField
    // when text field has text in it.
    // If text field is empty, the label is decorated as bodyLarge
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp
    ),
    // Used(misused?) for DatePicker
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    // Used(misused?) for Password fields
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        letterSpacing = 1.sp,
        fontSize = 23.sp
    ),
)
