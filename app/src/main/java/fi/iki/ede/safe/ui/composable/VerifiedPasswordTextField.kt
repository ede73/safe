package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import fi.iki.ede.crypto.Password
import kotlinx.coroutines.job

/**
 * Display one or two password fields (one + verification)
 */
@Composable
fun verifiedPasswordTextField(
    showVerification: Boolean,
    textTip: Int,
    verifyPassword: Int,
    modifier: Modifier = Modifier
): Password? {
    // Initial focus on first pwd field..
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        this.coroutineContext.job.invokeOnCompletion {
            focusRequester.requestFocus()
        }
    }

    val firstPassword = passwordTextField(
        textTip = textTip,
        modifier = modifier.focusRequester(focusRequester)
    )
    if (showVerification) {
        val secondPassword = passwordTextField(textTip = verifyPassword, modifier = modifier)
        if (firstPassword != secondPassword) {
            return null
        }
    }
    return firstPassword
}