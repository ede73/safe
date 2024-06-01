package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.password.highlightPassword
import fi.iki.ede.safe.ui.theme.LocalSafeColors
import fi.iki.ede.safe.ui.theme.LocalSafeFonts

@Composable
fun passwordTextField(
    textTip: Int,
    inputValue: String = "",
    onValueChange: (Password) -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    highlight: Boolean = true,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
    enableZoom: Boolean = false
) {
    val safeFonts = LocalSafeFonts.current
    val splitAt = 6
    var password by remember { mutableStateOf(TextFieldValue(text = inputValue)) }
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    var revealPassword = remember { mutableStateOf(false) }
    var isPasswordZoomed = remember { mutableStateOf(false) }

    TextField(
        value = password,
        onValueChange = {
            password = splitPassword(isPasswordZoomed.value, it, splitAt)

            if (false) {
                // kinda works if you type slow
                // tries to adjust to LF additions
                // AH IDEA!
                // TODO: cursorposition mod splitAt!
                // let US handle the adding of LF (or removal) so
                // we know EXACTLY where we are instead of automation below...

                val backwards = password.text.length >= it.text.length
                val newValue = splitPassword(isPasswordZoomed.value, it, splitAt)
                if (!backwards && isLinefeedLeft(newValue)) {
                    val s = newValue.selection.start + 1
                    password = TextFieldValue(
                        text = newValue.text,
                        selection = TextRange(s.coerceIn(0, newValue.text.length))
                    )
                } else {
                    password = newValue
                }
            }
            onValueChange(Password(joinPassword(password).text.toByteArray()))
        },
        label = { Text(stringResource(id = textTip)) },
        visualTransformation = showOrObfuscatePassword(
            revealPassword,
            highlight,
            password,
            isPasswordZoomed.value
        ),
        shape = RoundedCornerShape(20.dp),
        leadingIcon = {
            if (enableZoom)
                IconButton(onClick = {
                    isPasswordZoomed.value = !isPasswordZoomed.value
                    password = splitPassword(isPasswordZoomed.value, password, splitAt)
                }) {
                    Icon(
                        imageVector = if (isPasswordZoomed.value) Icons.Filled.Search else Icons.Filled.SearchOff,
                        contentDescription = null
                    )
                } else null
        },
        // such a shitshow...
        readOnly = isPasswordZoomed.value,
        trailingIcon = { showOrHidePassword(revealPassword) },
        singleLine = if (isPasswordZoomed.value) false else singleLine,
        maxLines = if (isPasswordZoomed.value) 10 else maxLines,
        colors = hideFocusLine,
        textStyle = if (isPasswordZoomed.value) safeFonts.zoomedPassword
        else textStyle ?: safeFonts.regularPassword,
        modifier = modifier
//        modifier = modifier.let {
//            if (isExpanded.value) modifier
//                .fillMaxWidth(fraction = 1f)
//                .fillMaxHeight(fraction = 1f) else it
//        }
    )
    //return Password(joinPassword(password).text.toByteArray())
}

fun isLinefeedLeft(newValue: TextFieldValue): Boolean {
    val s = (newValue.selection.start - 1).coerceIn(0, newValue.text.length)
    val e = (s + 1).coerceIn(s, newValue.text.length)
    return newValue.text.substring(s, e) == "\n"
}

@Composable
private fun showOrHidePassword(revealPassword: MutableState<Boolean>) =
    IconButton(onClick = { revealPassword.value = !revealPassword.value }) {
        Icon(
            imageVector = if (revealPassword.value) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = null
        )
    }

fun splitPassword(isPasswordZoomed: Boolean, password: TextFieldValue, size: Int = 6) =
    if (!isPasswordZoomed) password else
        password.copy(text = joinPassword(password).text.chunked(size).joinToString("\n"))

fun joinPassword(password: TextFieldValue) =
    password.copy(text = password.text.filter { it != '\n' })

@Composable
private fun showOrObfuscatePassword(
    revealPassword: MutableState<Boolean>,
    highlight: Boolean,
    password: TextFieldValue,
    isExpanded: Boolean
) = if (revealPassword.value || isExpanded) {
    val safeColors = LocalSafeColors.current
    VisualTransformation {
        if (highlight)
            highlightPassword(password.text, safeColors)
        else TransformedText(
            buildAnnotatedString { append(password.text) },
            OffsetMapping.Identity
        )
    }
} else {
    PasswordVisualTransformation()
}