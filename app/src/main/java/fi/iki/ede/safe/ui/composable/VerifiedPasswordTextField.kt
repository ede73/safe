package fi.iki.ede.safe.ui.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.job

/**
 * Display one or two password fields (one + verification)
 */
@Composable
fun VerifiedPasswordTextField(
    showVerification: Boolean,
    @StringRes
    textTip: Int,
    @StringRes
    verifyPassword: Int,
    modifier: Modifier = Modifier,
    onMatchingPasswords: (Password) -> Unit = {},
    stealFocus: Boolean = true // TODO: Terrible, remove! Refactor FocusRequests to call site
) {
    // Initial focus on first pwd field..
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (stealFocus) {
            this.coroutineContext.job.invokeOnCompletion {
                focusRequester.requestFocus()
            }
        }
    }

    var firstPassword by remember { mutableStateOf(Password.getEmpty()) }
    PasswordTextField(
        textTip = textTip,
        modifier = modifier
            .focusRequester(focusRequester),
        onValueChange = { firstPassword = it }
        //.semantics { contentDescription = "eka salasana" }
    )
    var secondPassword by remember { mutableStateOf(Password.getEmpty()) }
    if (showVerification) {
        PasswordTextField(
            textTip = verifyPassword,
            modifier = modifier,
            onValueChange = { secondPassword = it }
//                .semantics { contentDescription = "toka salasana" }
        )
        if (firstPassword == secondPassword) {
            onMatchingPasswords(firstPassword)
        }
    } else {
        onMatchingPasswords(firstPassword)
    }
}

@Preview(showBackground = true)
@Composable
fun VerifiedPasswordTextFieldPreview() {
    SafeTheme {
        Column {
            VerifiedPasswordTextField(
                showVerification = true,
                textTip = R.string.password_entry_username_tip,
                verifyPassword = R.string.password_entry_password_tip
            )
            HorizontalDivider(modifier = Modifier.padding(10.dp))
            VerifiedPasswordTextField(
                showVerification = false,
                textTip = R.string.password_entry_username_tip,
                verifyPassword = R.string.password_entry_password_tip
            )
        }
    }
}