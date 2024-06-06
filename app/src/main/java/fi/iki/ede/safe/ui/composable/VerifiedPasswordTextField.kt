package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import fi.iki.ede.crypto.Password
import kotlinx.coroutines.job

/**
 * Display one or two password fields (one + verification)
 */
@Composable
fun VerifiedPasswordTextField(
    showVerification: Boolean,
    textTip: Int,
    verifyPassword: Int,
    onMatchingPasswords: (Password) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Initial focus on first pwd field..
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        this.coroutineContext.job.invokeOnCompletion {
            focusRequester.requestFocus()
        }
    }

    var firstPassword by remember { mutableStateOf(Password.getEmpty()) }
    PasswordTextField(
        textTip = textTip,
        onValueChange = { firstPassword = it },
        modifier = modifier
            .focusRequester(focusRequester)
        //.semantics { contentDescription = "eka salasana" }
    )
    var secondPassword by remember { mutableStateOf(Password.getEmpty()) }
    if (showVerification) {
        PasswordTextField(
            textTip = verifyPassword,
            onValueChange = { secondPassword = it },
            modifier = modifier
//                .semantics { contentDescription = "toka salasana" }
        )
        if (firstPassword == secondPassword) {
            onMatchingPasswords(firstPassword)
        }
    } else {
        onMatchingPasswords(firstPassword)
    }
}