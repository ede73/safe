package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme

@Composable
fun EnterNewMasterPassword(
    onNewMasterPassword: (oldAndNewMasterPassword: Pair<Password, Password>) -> Unit
) {
    val passwordMinimumLength = integerResource(id = R.integer.password_minimum_length)

    Dialog(
        onDismissRequest = { onNewMasterPassword(Pair(Password.getEmpty(), Password.getEmpty())) },
        content = {
            Column {
                var oldPassword by remember { mutableStateOf(Password.getEmpty()) }
                var newPassword by remember { mutableStateOf(Password.getEmpty()) }
                PasswordTextField(textTip = R.string.action_bar_old_password,
                    onValueChange = { oldPassword = it })
                VerifiedPasswordTextField(
                    true,
                    R.string.login_password_tip,
                    R.string.login_verify_password_tip,
                    onMatchingPasswords = {
                        newPassword = it
                    },
                )
                SafeButton(
                    enabled = !newPassword.isEmpty() &&
                            !oldPassword.isEmpty() &&
                            newPassword != oldPassword &&
                            newPassword.length >= passwordMinimumLength,
                    onClick = { onNewMasterPassword(Pair(oldPassword, newPassword)) },
                    modifier = Modifier.testTag(TestTag.TEST_TAG_CHANGE_PASSWORD_OK)
                ) {
                    Text(text = stringResource(id = R.string.action_bar_change_password_ok))
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EnterNewMasterPasswordPreview() {
    SafeTheme {
        EnterNewMasterPassword {

        }
    }
}