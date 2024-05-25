package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.password.HighlightPassword

@Composable
fun passwordTextField(
    textTip: Int,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    singleLine: Boolean = true,
    maxLines: Int = 1,
    highlite: Boolean = true,
    modifier: Modifier = Modifier,
    updated: Boolean = false
): Password {
    // Since the PasswordTextField 'owns' its password state, when assigned from another mutable state
    // it cannot be updated by programmatic change, this mutable should be pulled out from PasswordTextField!
    // ie. change value:String to value:Mutable OR make accossor to actually change the password! on demand
    var password by remember { mutableStateOf(value) }
    // TODO: Enabling breaks initial login screen password entry(see above comment)
    // TODO: Disabling breaks generate password
    if (updated && value != password) {
        password = value
    }
    var revealPassword by remember { mutableStateOf(false) }
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    TextField(
        value = password,
        label = { Text(stringResource(id = textTip)) },
        visualTransformation = if (revealPassword) {
            if (highlite) {
                VisualTransformation { HighlightPassword.hilite(password) }
            } else {
                VisualTransformation.None
            }
        } else {
            PasswordVisualTransformation()
        },
        shape = RoundedCornerShape(20.dp),
        trailingIcon = {
            IconButton(onClick = { revealPassword = !revealPassword }) {
                Icon(
                    imageVector = if (revealPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null
                )
            }
        },
        onValueChange = {
            password = it
            onValueChange(it)
        },
        singleLine = singleLine,
        maxLines = maxLines,
        colors = hideFocusLine,
        modifier = modifier
    )
    return Password(password.toByteArray())
}