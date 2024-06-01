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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    value: String = "",
    onValueChange: (String) -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    highlight: Boolean = true,
    modifier: Modifier = Modifier,
    updated: Boolean = false,
    textStyle: TextStyle? = null
): Password {
    val safeFonts = LocalSafeFonts.current

    // Since the PasswordTextField 'owns' its password state, when assigned from another mutable state
    // it cannot be updated by programmatic change, this mutable should be pulled out from PasswordTextField!
    // ie. change value:String to value:Mutable OR make accessor to actually change the password! on demand
    var password by remember { mutableStateOf(value) }
    if (updated && value != password) {
        password = value
    }
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    var revealPassword = remember { mutableStateOf(false) }
    var isExpanded = remember { mutableStateOf(false) }

    TextField(
        value = password,
        onValueChange = {
            password = it
            onValueChange(it)
        },
        label = { Text(stringResource(id = textTip)) },
        visualTransformation = showOrObfuscatePassword(
            revealPassword,
            highlight,
            password,
            isExpanded.value
        ),
        shape = RoundedCornerShape(20.dp),
        leadingIcon = { regularOrZoomedIcon(isExpanded) },
        trailingIcon = { showOrHidePassword(revealPassword) },
        singleLine = if (isExpanded.value) false else singleLine,
        maxLines = if (isExpanded.value) 10 else maxLines,
        colors = hideFocusLine,
        textStyle = if (isExpanded.value) safeFonts.zoomedPassword
        else textStyle ?: safeFonts.regularPassword,
        modifier = modifier
    )
    return Password(password.toByteArray())
}

@Composable
private fun showOrHidePassword(revealPassword: MutableState<Boolean>) =
    IconButton(onClick = { revealPassword.value = !revealPassword.value }) {
        Icon(
            imageVector = if (revealPassword.value) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = null
        )
    }


@Composable
private fun regularOrZoomedIcon(isExpanded: MutableState<Boolean>) =
    IconButton(onClick = { isExpanded.value = !isExpanded.value }) {
        Icon(
            imageVector = if (isExpanded.value) Icons.Filled.Search else Icons.Filled.SearchOff,
            contentDescription = null
        )
    }

@Composable
private fun showOrObfuscatePassword(
    revealPassword: MutableState<Boolean>,
    highlight: Boolean,
    password: String,
    isExpanded: Boolean
) = if (revealPassword.value || isExpanded) {
    val visualizeString = if (isExpanded) password.chunked(6).joinToString("\n") else password
    val safeColors = LocalSafeColors.current
    VisualTransformation {
        if (highlight) highlightPassword(visualizeString, safeColors)
        else TransformedText(
            buildAnnotatedString { password },
            OffsetMapping.Identity
        )
    }
} else {
    PasswordVisualTransformation()
}