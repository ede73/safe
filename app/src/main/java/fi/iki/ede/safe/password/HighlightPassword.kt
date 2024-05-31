package fi.iki.ede.safe.password

import android.text.TextUtils
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle
import fi.iki.ede.safe.ui.theme.lettersL
import fi.iki.ede.safe.ui.theme.numbers108652
import fi.iki.ede.safe.ui.theme.whiteSpaceL

object HighlightPassword {
    /**
     * Some letters remind each other and may be difficult to recognize on a totally random string
     *
     * Numbers in Blue, lower cases in l (all rest are capitals)
     *
     * l, I, 1
     * O, 0
     * B, 8
     * G, 6
     * S, 5
     * Z, 2
     */
    fun highlight(password: String): TransformedText =
        if (TextUtils.isEmpty(password))
            TransformedText(buildAnnotatedString { append("") }, OffsetMapping.Identity)
        else TransformedText(
            buildAnnotatedString {
                password.forEach {
                    when (it) {
                        '1', '0', '8', '6', '5', '2' -> withStyle(style = SpanStyle(background = numbers108652)) {
                            append(it)
                        }

                        'l' -> withStyle(style = SpanStyle(background = lettersL)) {
                            append(it)
                        }

                        else ->
                            if (it.isWhitespace())
                                withStyle(style = SpanStyle(background = whiteSpaceL)) {
                                    append(it)
                                }
                            else append(it)
                    }
                }
            },
            OffsetMapping.Identity
        )
}