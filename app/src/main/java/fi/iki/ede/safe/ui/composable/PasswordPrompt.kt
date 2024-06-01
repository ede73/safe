package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag

@Composable
fun PasswordPrompt(
    firstTimeInitialize: Boolean,
    modifier: Modifier = Modifier,
    goodPasswordEntered: (password: Password) -> Unit,
) {
    val passwordMinimumLength = integerResource(id = R.integer.password_minimum_length)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(imageVector = Icons.Default.Password, contentDescription = null)
            Text(text = stringResource(id = R.string.application_name))
        }
        Text(text = stringResource(id = R.string.login_tip))
        var verifiedPassword: Password by remember { mutableStateOf(Password.getEmpty()) }
        verifiedPasswordTextField(
            firstTimeInitialize,
            R.string.login_password_tip,
            R.string.login_verify_password_tip,
            onMatchingPasswords = {
                verifiedPassword = it
            },
            modifier.testTag(TestTag.TEST_TAG_PASSWORD_PROMPT)
        )

        Button(
            enabled = if (firstTimeInitialize) verifiedPassword != null && verifiedPassword.length >= passwordMinimumLength else !(verifiedPassword == null || verifiedPassword.isEmpty()),
            onClick = {
                goodPasswordEntered(verifiedPassword)
            },
            modifier = Modifier.testTag(TestTag.TEST_TAG_LOGIN_BUTTON)
        ) { Text(stringResource(id = R.string.login_button)) }
    }
}