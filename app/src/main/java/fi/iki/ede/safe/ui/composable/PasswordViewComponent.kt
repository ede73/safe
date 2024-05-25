package fi.iki.ede.safe.ui.composable

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelper
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.hibp.BreachCheck
import fi.iki.ede.hibp.KAnonymity
import fi.iki.ede.safe.R
import fi.iki.ede.safe.clipboard.ClipboardUtils
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.activities.AvertInactivityDuringLongTask
import fi.iki.ede.safe.ui.activities.EditingPasswordViewModel
import fi.iki.ede.safe.ui.activities.PasswordEntryScreen

@Composable
fun PasswordViewComponent(
    viewModel: EditingPasswordViewModel,
    modifier: Modifier = Modifier
) {
    val passwordLength = integerResource(id = R.integer.password_default_length)
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()
    val passEntry by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    var breachCheckResult by remember { mutableStateOf(BreachCheckEnum.NOT_CHECKED) }

    val padding = Modifier.padding(6.dp)
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    var passwordWasUpdated by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        TopActionBarForPasswordView {
            passwordWasUpdated = true
            viewModel.updatePassword(
                PasswordGenerator.genPassword(
                    passUpper = true,
                    passLower = true,
                    passNum = true,
                    passSymbol = true,
                    length = passwordLength
                ).encrypt(ks)
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextField(
                value = passEntry.description,
                onValueChange = {
                    viewModel.updateDescription(it)
                },
                label = { Text(stringResource(id = R.string.password_entry_description_tip)) },
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = hideFocusLine,
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            val website = passEntry.website
            Button(enabled = !TextUtils.isEmpty(website) && Uri.parse(website) != null, onClick = {
                val uri = if (website.lowercase().startsWith("http://") || website.lowercase()
                        .startsWith("https://")
                ) {
                    Uri.parse(website)
                } else {
                    Uri.parse("https://$website")
                }
                // Without scheme, ACTION_VIEW will fail
                if (uri != null && uri.scheme != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }) { Text(stringResource(id = R.string.password_entry_visit)) }
            Spacer(Modifier.weight(1f))
            TextField(
                value = passEntry.website,
                onValueChange = {
                    viewModel.updateWebSite(it)
                },
                label = { Text(stringResource(id = R.string.password_entry_website_tip)) },
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = hideFocusLine,
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.username.decrypt(ks))
            }) { Text(stringResource(id = R.string.password_entry_username_label)) }
            Spacer(Modifier.weight(1f))
            passwordTextField(
                textTip = R.string.password_entry_username_tip,
                value = passEntry.username.decrypt(ks),
                onValueChange = {
                    viewModel.updateUsername(it.encrypt(ks))
                },
                highlight = false,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(id = R.string.password_entry_highlight_hint))
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.password.decrypt(ks))
            }) { Text(stringResource(id = R.string.password_entry_password_label)) }
            Spacer(Modifier.weight(1f))
            passwordTextField(
                textTip = R.string.password_entry_password_tip,
                value = passEntry.password.decrypt(ks),
                updated = passwordWasUpdated,
                onValueChange = {
                    viewModel.updatePassword(it.encrypt(ks))
                    breachCheckResult = BreachCheckEnum.NOT_CHECKED
                },
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
            )
            passwordWasUpdated = false
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            if (breachCheckResult == BreachCheckEnum.NOT_CHECKED) {
                Button(onClick = {
                    BreachCheck.doBreachCheck(
                        KAnonymity(passEntry.password.decrypt(ks)),
                        context,
                        { breached ->
                            breachCheckResult = when (breached) {
                                true -> BreachCheckEnum.BREACHED
                                false -> BreachCheckEnum.NOT_BREACHED
                            }
                        },
                        { error -> Log.e(PasswordEntryScreen.TAG, "Error: $error") })
                }) { Text(stringResource(id = R.string.password_entry_breach_check)) }
            }
            when (breachCheckResult) {
                BreachCheckEnum.BREACHED -> Text(stringResource(id = R.string.password_entry_breached))

                BreachCheckEnum.NOT_BREACHED -> Text(stringResource(id = R.string.password_entry_not_breached))

                else -> {}
            }
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
//            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = 1578096000000)
//            DatePicker(
//                state = datePickerState,
//                title = { Text("Password changed data") },
//                modifier = Modifier.padding(16.dp),
//                showModeToggle = true,
//            )

            if (passEntry.passwordChangedDate != null) {
                Text(
                    text = stringResource(
                        id = R.string.password_entry_changed_date,
                        passEntry.passwordChangedDate.toString()
                    ), modifier = modifier
                )
            }
        }
        passwordTextField(
            textTip = R.string.password_entry_note_tip,
            value = passEntry.note.decrypt(ks),
            onValueChange = {
                viewModel.updateNote(it.encrypt(ks))
            },
            singleLine = false,
            maxLines = 22,
            highlight = false,
            modifier = Modifier.fillMaxWidth(),
        )

        SafePhoto(
            (context as AvertInactivityDuringLongTask),
            photo = passEntry.plainPhoto,
            onBitmapCaptured = {
                val samePhoto = it?.sameAs(passEntry.plainPhoto) ?: (passEntry.plainPhoto == null)
                if (!samePhoto) {
                    viewModel.updatePhoto(it)
                }
            })
    }
}

private fun String.encrypt(ks: KeyStoreHelper) = ks.encryptByteArray(this.trim().toByteArray())
private fun IVCipherText.decrypt(ks: KeyStoreHelper) =
    String(ks.decryptByteArray(this))

enum class BreachCheckEnum {
    NOT_CHECKED, BREACHED, NOT_BREACHED
}