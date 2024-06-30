package fi.iki.ede.safe.ui.composable

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.LoginPrecondition
import fi.iki.ede.safe.ui.activities.LoginStyle
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.delay

private const val TAG = "LoginPasswordPrompts"

@Composable
fun LoginPasswordPrompts(
    loginPrecondition: LoginPrecondition,
    modifier: Modifier = Modifier,
    goodPasswordEntered: (loginStyle: LoginStyle, password: Password) -> Boolean,
) {
    var loginStyle by remember { mutableStateOf(loginPrecondition) }
    //var loginInProgress by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    val passwordMinimumLength = integerResource(id = R.integer.password_minimum_length)

    LaunchedEffect(wrongPassword) {
        if (wrongPassword) {
            delay(1000) // Duration of the effect
            wrongPassword = false
        }
    }

//    val color = animateColorAsState(
//        targetValue = if (wrongPassword) Color.Red else Color.Transparent,
//        animationSpec = infiniteRepeatable(
//            animation = keyframes {
//                durationMillis = 1000
//                Color.Red at 500
//                Color.Transparent at 1000
//            },
//            repeatMode = RepeatMode.Reverse
//        ), label = "Wrong password"
//    )
//
//    val shake = animateOffsetAsState(
//        targetValue = if (wrongPassword) Offset(10f, 0f) else Offset.Zero,
//        animationSpec = infiniteRepeatable(
//            animation = tween(durationMillis = 50, easing = LinearEasing),
//            repeatMode = RepeatMode.Reverse
//        ), label = "Wrong password"
//    )
    val color = animateColorAsState(
        targetValue = if (wrongPassword) Color.Red else Color.Transparent,
        animationSpec = tween(durationMillis = 500)
    )

    val shake = animateDpAsState(
        targetValue = if (wrongPassword) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 50)
    )
    Column {
        Text(text = stringResource(id = R.string.login_tip))
        var verifiedPassword: Password by remember { mutableStateOf(Password.getEmpty()) }
        VerifiedPasswordTextField(
            loginStyle == LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE,
            R.string.login_password_tip,
            R.string.login_verify_password_tip,
            modifier
                .testTag(TestTag.PASSWORD_PROMPT)
                .border(width = 2.dp, color = color.value)
                .graphicsLayer {
                    translationX = shake.value.toPx()
                    translationY = shake.value.toPx()
                },
            onMatchingPasswords = {
                verifiedPassword = it
            }
        )

        SafeButton(
            modifier = Modifier.testTag(TestTag.LOGIN_BUTTON),
            onClick = {
                val whatToDoAfterLogin = when (loginStyle) {
                    LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE -> LoginStyle.EXISTING_LOGIN
                    LoginPrecondition.NORMAL_LOGIN -> LoginStyle.EXISTING_LOGIN
                    LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE -> LoginStyle.FIRST_TIME_LOGIN_CLEAR_DATABASE
                }
                if (!goodPasswordEntered(whatToDoAfterLogin, verifiedPassword)) {
                    wrongPassword = true
                }
            },
            enabled = /*!loginInProgress &&*/ (if (loginStyle == LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE)
                verifiedPassword != null && verifiedPassword.length >= passwordMinimumLength
            else !(verifiedPassword == null || verifiedPassword.isEmpty()))
        ) {
            when (loginStyle) {
                LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE -> Text(stringResource(id = R.string.login_button))
                LoginPrecondition.NORMAL_LOGIN -> Text(stringResource(id = R.string.login_button))
                LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE -> Text(stringResource(id = R.string.login_button_clear_db))
            }
        }

        if (loginPrecondition == LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE) {
            SafeButton(
                modifier = Modifier.testTag(TestTag.LOGIN_ANEW_BUTTON),
                onClick = {
                    loginStyle = when (loginStyle) {
                        LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE -> LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE
                        LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE -> LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE
                        LoginPrecondition.NORMAL_LOGIN -> throw Exception("Normal login here is not possible")
                    }
                },
            ) {
                when (loginStyle) {
                    LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE -> Text(stringResource(id = R.string.login_anew_button))
                    LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE -> Text(stringResource(id = R.string.login_anew_retry_button))
                    LoginPrecondition.NORMAL_LOGIN -> {}
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordPromptPreview() {
    SafeTheme {
        Column {
            Text(text = "------ First time init")
            LoginPasswordPrompts(LoginPrecondition.FIRST_TIME_LOGIN_EMPTY_DATABASE) { _, _ ->
                Log.d(TAG, "Got a good password")
                true
            }
            Text(text = "------ First time init")
            LoginPasswordPrompts(LoginPrecondition.NORMAL_LOGIN) { _, _ ->
                Log.d(TAG, "Got a good password")
                true
            }
            Text(text = "------ Normal login")
            LoginPasswordPrompts(LoginPrecondition.FIRST_TIME_LOGIN_RESTORED_DATABASE) { _, _ ->
                Log.d(TAG, "Got a good password")
                true
            }
        }
    }
}