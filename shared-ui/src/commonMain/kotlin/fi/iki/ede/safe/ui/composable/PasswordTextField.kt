package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.password.highlightPassword
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.LocalSafeTheme

@Composable
fun PasswordTextField(
    label: String,
    modifier: Modifier = Modifier,
    inputValue: String = "",
    onValueChange: (Password) -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    highlight: Boolean = true,
    textStyle: TextStyle? = null,
    enableZoom: Boolean = false
) {
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val isPasswordZoomed = remember { mutableStateOf(false) }
    val safeTheme = LocalSafeTheme.current
    val splitAt = 6
    var password by remember { mutableStateOf(TextFieldValue(text = inputValue)) }
    val revealPassword = remember { mutableStateOf(false) }

    LaunchedEffect(inputValue) {
        password = password.copy(text = if (isPasswordZoomed.value) splitPassword(TextFieldValue(inputValue), splitAt).text else inputValue)
    }

    TextField(
        value = password,
        onValueChange = {
            password = if (isPasswordZoomed.value) splitPassword(it, splitAt) else it
            val joined = if (isPasswordZoomed.value) joinPassword(password) else password
            onValueChange(Password(joined.text.encodeToByteArray()))
        },
        label = { Text(label) },
        visualTransformation = showOrObfuscatePassword(
            revealPassword,
            highlight,
            password,
            isPasswordZoomed.value
        ),
        leadingIcon = {
            if (enableZoom)
                IconButton(onClick = {
                    isPasswordZoomed.value = !isPasswordZoomed.value
                    password =
                        if (isPasswordZoomed.value) splitPassword(password, splitAt) else joinPassword(password)
                }) {
                    Icon(
                        imageVector = if (isPasswordZoomed.value) Icons.Filled.Search else Icons.Filled.SearchOff,
                        contentDescription = null
                    )
                } else null
        },
        readOnly = isPasswordZoomed.value,
        trailingIcon = { ShowOrHidePassword(revealPassword) },
        singleLine = if (isPasswordZoomed.value) false else singleLine,
        maxLines = if (isPasswordZoomed.value) 10 else maxLines,
        colors = hideFocusLine,
        textStyle = if (isPasswordZoomed.value) safeTheme.customFonts.zoomedPassword
        else textStyle ?: safeTheme.customFonts.regularPassword,
        modifier = modifier
            .testTag(TestTag.PASSWORD_TEXT_FIELD)
    )
}

@Composable
private fun ShowOrHidePassword(revealPassword: MutableState<Boolean>) =
    IconButton(onClick = {
        revealPassword.value = !revealPassword.value
    }) {
        Icon(
            imageVector = if (revealPassword.value) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = null
        )
    }

private fun splitPassword(password: TextFieldValue, size: Int = 6): TextFieldValue {
    val joined = joinPassword(password)
    val splitText = joined.text.chunked(size).joinToString("\n")
    val newStart = adjustOffsetForSplit(joined.text, joined.selection.start, size)
    val newEnd = adjustOffsetForSplit(joined.text, joined.selection.end, size)
    return password.copy(
        text = splitText,
        selection = TextRange(newStart.coerceIn(0, splitText.length), newEnd.coerceIn(0, splitText.length))
    )
}

private fun joinPassword(password: TextFieldValue): TextFieldValue {
    val joinedText = password.text.filter { it != '\n' }
    val newStart = adjustOffset(password.text, password.selection.start)
    val newEnd = adjustOffset(password.text, password.selection.end)
    return password.copy(
        text = joinedText,
        selection = TextRange(newStart.coerceIn(0, joinedText.length), newEnd.coerceIn(0, joinedText.length))
    )
}

private fun adjustOffset(originalText: String, offset: Int): Int {
    var newlines = 0
    val limit = offset.coerceAtMost(originalText.length)
    for (i in 0 until limit) {
        if (originalText[i] == '\n') {
            newlines++
        }
    }
    return offset - newlines
}

private fun adjustOffsetForSplit(joinedText: String, offset: Int, size: Int): Int {
    var newlines = 0
    val limit = offset.coerceAtMost(joinedText.length)
    for (i in 1..limit) {
        if (i > 0 && i % size == 0 && i < joinedText.length) {
            newlines++
        }
    }
    return offset + newlines
}

@Composable
private fun showOrObfuscatePassword(
    revealPassword: MutableState<Boolean>,
    highlight: Boolean,
    password: TextFieldValue,
    isExpanded: Boolean
) = if (revealPassword.value || isExpanded) {
    val safeTheme = LocalSafeTheme.current
    VisualTransformation {
        if (highlight)
            highlightPassword(password.text, safeTheme.customColors)
        else TransformedText(
            buildAnnotatedString { append(password.text) },
            OffsetMapping.Identity
        )
    }
} else {
    PasswordVisualTransformation()
}

@Preview
@Composable
fun PasswordTextFieldPreview() {
    Column {
        PasswordTextField(
            label = "Password",
            inputValue = "1234567890",
            onValueChange = {}
        )
    }
}
