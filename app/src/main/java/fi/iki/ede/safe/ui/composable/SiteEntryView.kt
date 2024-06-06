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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.hibp.BreachCheck
import fi.iki.ede.hibp.KAnonymity
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.R
import fi.iki.ede.safe.clipboard.ClipboardUtils
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.activities.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.activities.SiteEntryEditScreen
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.utilities.AvertInactivityDuringLongTask
import java.time.ZonedDateTime

@Composable
fun SiteEntryView(
    viewModel: EditingSiteEntryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val ks = KeyStoreHelperFactory.getKeyStoreHelper()
    val padding = Modifier.padding(6.dp)
    val passEntry by viewModel.uiState.collectAsState()
    val passwordLength = integerResource(id = R.integer.password_default_length)
    val safeTheme = LocalSafeTheme.current
    var breachCheckResult by remember { mutableStateOf(BreachCheckEnum.NOT_CHECKED) }
    var passwordWasUpdated by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        TopActionBarForSiteEntryView {
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
                colors = hideFocusLine,
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            val website = passEntry.website
            SafeButton(
                enabled = !TextUtils.isEmpty(website) && Uri.parse(website) != null,
                onClick = {
                    val uri = tryParseUri(website)
                    // Without scheme, ACTION_VIEW will fail
                    if (uri.scheme != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }) { Text(stringResource(id = R.string.password_entry_visit)) }
            Spacer(Modifier.weight(1f))
            TextField(
                value = passEntry.website,
                onValueChange = { viewModel.updateWebSite(it) },
                label = { Text(stringResource(id = R.string.password_entry_website_tip)) },
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                colors = hideFocusLine,
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            SafeButton(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.username.decrypt(ks))
            }) { Text(stringResource(id = R.string.password_entry_username_label)) }
            Spacer(Modifier.weight(1f))
            PasswordTextField(
                textTip = R.string.password_entry_username_tip,
                inputValue = passEntry.username.decrypt(ks),
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
            val text = buildAnnotatedString {
                append(stringResource(id = R.string.password_entry_highlight_hint))
                withStyle(style = SpanStyle(background = safeTheme.customColors.numbers108652)) {
                    append("108652, ")
                }
                append(stringResource(id = R.string.password_entry_highlight_hint_l))
                withStyle(style = SpanStyle(background = safeTheme.customColors.lettersL)) {
                    append("L(l), ")
                }
                append(stringResource(id = R.string.password_entry_highlight_space))
                withStyle(style = SpanStyle(background = safeTheme.customColors.whiteSpaceL)) {
                    append(" ")
                }
            }
            Text(
                text = text,
                style = safeTheme.customFonts.smallNote,
            )
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            SafeButton(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.password.decrypt(ks))
            }) { Text(stringResource(id = R.string.password_entry_password_label)) }
            Spacer(Modifier.weight(1f))
            PasswordTextField(
                textTip = R.string.password_entry_password_tip,
                inputValue = passEntry.password.decrypt(ks),
                //updated = passwordWasUpdated,
                onValueChange = {
                    viewModel.updatePassword(it.encrypt(ks))
                    breachCheckResult = BreachCheckEnum.NOT_CHECKED
                },
                enableZoom = true,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                textStyle = safeTheme.customFonts.regularPassword
            )
            passwordWasUpdated = false
        }
        if (BuildConfig.ENABLE_HIBP) {
            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                if (breachCheckResult == BreachCheckEnum.NOT_CHECKED) {
                    SafeButton(onClick = {
                        BreachCheck.doBreachCheck(
                            KAnonymity(passEntry.password.decrypt(ks)),
                            context,
                            { breached ->
                                breachCheckResult = when (breached) {
                                    true -> BreachCheckEnum.BREACHED
                                    false -> BreachCheckEnum.NOT_BREACHED
                                }
                            },
                            { error -> Log.e(SiteEntryEditScreen.TAG, "Error: $error") })
                    }) { Text(stringResource(id = R.string.password_entry_breach_check)) }
                }
                when (breachCheckResult) {
                    BreachCheckEnum.BREACHED -> Text(stringResource(id = R.string.password_entry_breached))

                    BreachCheckEnum.NOT_BREACHED -> Text(stringResource(id = R.string.password_entry_not_breached))

                    else -> {}
                }
            }
        }
        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            DatePicker(
                zonedDateTime = passEntry.passwordChangedDate,
                onValueChange = { date: ZonedDateTime? ->
                    viewModel.updatePasswordChangedDate(date)
                })
        }
        PasswordTextField(
            textTip = R.string.password_entry_note_tip,
            inputValue = passEntry.note.decrypt(ks),
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

private fun tryParseUri(website: String): Uri =
    if (website.lowercase().startsWith("http://") ||
        website.lowercase().startsWith("https://")
    ) Uri.parse(website)
    else Uri.parse("https://$website")


enum class BreachCheckEnum {
    NOT_CHECKED, BREACHED, NOT_BREACHED
}
