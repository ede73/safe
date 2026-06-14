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
        password = password.copy(text = inputValue)
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
                        if (isPasswordZoomed.value) splitPassword(password, splitAt) else password
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

private fun splitPassword(password: TextFieldValue, size: Int = 6) =
    password.copy(text = joinPassword(password).text.chunked(size).joinToString("\n"))

private fun joinPassword(password: TextFieldValue) =
    password.copy(text = password.text.filter { it != '\n' })

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
