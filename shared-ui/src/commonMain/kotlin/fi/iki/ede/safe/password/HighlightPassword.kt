package fi.iki.ede.safe.password

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle
import fi.iki.ede.theme.SafeColors

fun highlightPassword(
    password: String,
    colors: SafeColors,
): TransformedText {
    return if (password.isBlank())
        TransformedText(buildAnnotatedString { append("") }, OffsetMapping.Identity)
    else {
        return TransformedText(
            buildAnnotatedString {
                password.forEach {
                    when (it) {
                        '1', '0', '8', '6', '5', '2' -> withStyle(style = SpanStyle(background = colors.numbers108652)) {
                            append(it)
                        }

                        'l' -> withStyle(style = SpanStyle(background = colors.lettersL)) {
                            append(it)
                        }

                        else ->
                            if (it.isWhitespace())
                                withStyle(style = SpanStyle(background = colors.whiteSpaceL)) {
                                    append(it)
                                }
                            else append(it)
                    }
                }
            },
            OffsetMapping.Identity
        )
    }
}
