package fi.iki.ede.safe.ui.composable

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.TextUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.autolock.AvertInactivityDuringLongTask
import fi.iki.ede.clipboardutils.ClipboardUtils
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.datepicker.DatePicker
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.dialogs.ShowLinkedGpmsDialog
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safephoto.SafePhoto
import fi.iki.ede.theme.LocalSafeTheme
import fi.iki.ede.theme.SafeButton
import fi.iki.ede.theme.SafeTextButton
import fi.iki.ede.theme.SafeTheme
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

inline fun <T, C : Collection<T>> C.ifNotEmpty(block: (C) -> Unit): C {
    if (this.isNotEmpty()) block(this)
    return this
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteEntryView(
    viewModel: EditingSiteEntryViewModel,
    modifier: Modifier = Modifier,
    skipForPreviewToWork: Boolean = false
) {
    val context = LocalContext.current
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val decrypter = KeyStoreHelperFactory.getDecrypter()
    val encrypter = KeyStoreHelperFactory.getEncrypter()
    val padding = Modifier.padding(6.dp)
    val passEntry by viewModel.editableSiteEntryState.collectAsState()
    val passwordLength = integerResource(id = R.integer.password_default_length)
    val safeTheme = LocalSafeTheme.current
    var passwordWasUpdated by remember { mutableStateOf(false) }
    var showLinkedInfo by remember { mutableStateOf<Set<SavedGPM>?>(null) }
    var showCustomPasswordGenerator by remember {
        mutableStateOf(false)
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    if (showCustomPasswordGenerator) {
        PopCustomPasswordDialog {
            // on dismiss
            if (it != null) {
                viewModel.updatePassword(it.encrypt(encrypter))
                passwordWasUpdated = true
                viewModel.updatePasswordChangedDate(ZonedDateTime.now())
            }
            showCustomPasswordGenerator = false
        }
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        TopActionBarForSiteEntryView { custom ->
            if (custom) {
                // some sites have annoying limits like you cant use this is that special char
                // or you HAVE to use this or that special char
                showCustomPasswordGenerator = true
            } else {
                passwordWasUpdated = true
                viewModel.updatePassword(
                    PasswordGenerator.genPassword(
                        passUpper = true,
                        passLower = true,
                        passNum = true,
                        passSymbols = true,
                        length = passwordLength
                    ).encrypt(encrypter)
                )
                viewModel.updatePasswordChangedDate(ZonedDateTime.now())
            }
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
                    .fillMaxWidth()
                    .testTag(TestTag.SITE_ENTRY_DESCRIPTION),
                colors = hideFocusLine,
            )
        }

        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            val website = passEntry.website
            SafeButton(
                onClick = {
                    val uri = tryParseUri(website)
                    // Without scheme, ACTION_VIEW will fail
                    if (uri.scheme != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                },
                enabled = !TextUtils.isEmpty(website) && Uri.parse(website) != null,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = stringResource(id = R.string.password_entry_visit)
                )
            }
            Spacer(Modifier.weight(1f))
            TextField(
                value = passEntry.website,
                onValueChange = { viewModel.updateWebSite(it) },
                label = { Text(stringResource(id = R.string.password_entry_website_tip)) },
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .testTag(TestTag.SITE_ENTRY_WEBSITE),
                colors = hideFocusLine,
            )
        }

        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            SafeButton(
                onClick = {
                    ClipboardUtils.addToClipboard(
                        context,
                        passEntry.username.decrypt(decrypter),
                        Preferences.getClipboardClearDelaySecs()
                    )
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(id = R.string.password_entry_username_label)
                )
            }
            Spacer(Modifier.weight(1f))
            PasswordTextField(
                textTip = R.string.password_entry_username_tip,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .testTag(TestTag.SITE_ENTRY_USERNAME),
                inputValue = passEntry.username.decrypt(decrypter),
                onValueChange = {
                    viewModel.updateUsername(it.encrypt(encrypter))
                },
                highlight = false,
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
            // some sites required OLD password AND new password (even if you are already
            // logged in and hence clearly knowledgeable of the old password), to avoid copy
            // paste hell, let's notice the situation and allow copying original and new separately!
            // TODO: not good enough
            Column {
                if (viewModel.originalPassword != null && passEntry.password.decrypt(decrypter) != viewModel.originalPassword?.decrypt(
                        decrypter
                    )
                ) {
                    SafeButton(
                        onClick = {
                            ClipboardUtils.addToClipboard(
                                context,
                                viewModel.originalPassword?.decrypt(decrypter),
                                Preferences.getClipboardClearDelaySecs()
                            )
                        }, contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            modifier = Modifier
                                .padding(0.dp)
                                .size(20.dp),
                            contentDescription = stringResource(id = R.string.password_entry_username_label)
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            modifier = Modifier
                                .padding(0.dp)
                                .size(20.dp),
                            contentDescription = stringResource(id = R.string.password_entry_username_label)
                        )
                    }
                }
                SafeButton(
                    modifier = Modifier.padding(0.dp),
                    onClick = {
                        ClipboardUtils.addToClipboard(
                            context,
                            passEntry.password.decrypt(decrypter),
                            Preferences.getClipboardClearDelaySecs()
                        )
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        modifier = Modifier
                            .padding(0.dp)
                            .size(20.dp),
                        contentDescription = stringResource(id = R.string.password_entry_username_label)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PasswordTextField(
                textTip = R.string.password_entry_password_tip,
                modifier = modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .testTag(TestTag.SITE_ENTRY_PASSWORD),
                inputValue = passEntry.password.decrypt(decrypter),
                onValueChange = {
                    viewModel.updatePassword(it.encrypt(encrypter))
                },
                textStyle = safeTheme.customFonts.regularPassword,
                enableZoom = true
            )
            passwordWasUpdated = false
        }

        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            if (!skipForPreviewToWork) {
                breachCheckButton(context, passEntry.password)()
            }
        }

        Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = stringResource(id = R.string.password_entry_changed_date))
                DatePicker(
                    zonedDateTime = passEntry.passwordChangedDate,
                    onValueChange = { date: ZonedDateTime? ->
                        viewModel.updatePasswordChangedDate(date)
                    })
                passEntry.id?.let { pid ->
                    // TODO: inject from GPMUI rather (yes, gonna be tricky)
                    val gpms = GPMDataModel.getLinkedGPMs(pid)
                    gpms.ifNotEmpty {
                        Box(modifier = Modifier.clickable {
                            showLinkedInfo = gpms
                        }) {
                            Text(text = "Has ${it.size} linked GPMs")
                        }
                    }
                }
            }
        }

        SiteEntryExtensionList(viewModel)

        PasswordTextField(
            textTip = R.string.password_entry_note_tip,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTag.SITE_ENTRY_NOTE)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            inputValue = passEntry.note.decrypt(decrypter),
            onValueChange = { viewModel.updateNote(it.encrypt(encrypter)) },
            singleLine = false,
            maxLines = 22,
            highlight = false,
        )

        if (context is AvertInactivityDuringLongTask) {
            SafePhoto(
                { isPausedOrResume, why ->
                    if (isPausedOrResume) {
                        (context as AvertInactivityDuringLongTask).pauseInactivity(context, why)
                    } else {
                        (context as AvertInactivityDuringLongTask).resumeInactivity(context, why)

                    }
                },
                currentPhoto = passEntry.plainPhoto,
                onBitmapCaptured = {
                    val samePhoto =
                        it?.sameAs(passEntry.plainPhoto) ?: (passEntry.plainPhoto == null)
                    if (!samePhoto) {
                        viewModel.updatePhoto(it)
                    }
                },
                photoPermissionRequiredContent = { oldPhoto, onBitmapCaptured, askPermission ->
                    TakePhotoOrAskPermission(
                        askPermission,
                        oldPhoto,
                        onBitmapCaptured
                    )
                },
                takePhotoContent = { oldPhoto, onBitmapCaptured, takePhoto ->
                    TakePhotoOrAskPermission(
                        takePhoto,
                        oldPhoto,
                        onBitmapCaptured
                    )
                },
                composeTakePhoto = { takePhoto ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        SafeTextButton(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            onClick = {
                                takePhoto()
                            }) { Text(text = stringResource(id = R.string.password_entry_capture_photo)) }
                    }
                }
            )
        }

        if (showLinkedInfo != null) {
            ShowLinkedGpmsDialog(showLinkedInfo!!, onDismiss = { showLinkedInfo = null })
        }
    }
}

@Composable
private fun TakePhotoOrAskPermission(
    askPermission: MutableState<Boolean>,
    oldPhoto: Bitmap?,
    onBitmapCaptured: (Bitmap?) -> Unit
) {
    Row {
        SafeTextButton(onClick = { askPermission.value = true }) {
            Text(text = stringResource(id = R.string.password_entry_capture_photo))
        }
        if (oldPhoto != null) {
            SafeTextButton(onClick = { onBitmapCaptured(null) }) {
                Text(text = stringResource(id = R.string.password_entry_delete_photo))
            }
        }
    }
}

private fun tryParseUri(website: String): Uri =
    if (website.lowercase().startsWith("http://") ||
        website.lowercase().startsWith("https://")
    ) Uri.parse(website)
    else Uri.parse("https://$website")


@Preview(showBackground = true)
@Composable
fun SiteEntryViewPreview() {
    SafeTheme {
        //PopCustomPasswordDialog {}
        KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
        KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
        val encrypter = KeyStoreHelperFactory.getEncrypter()
        val site1 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description1".toByteArray())
            id = 1
            website = encrypter("Website".toByteArray())
            username = encrypter("Username".toByteArray())
            password = encrypter("Password".toByteArray())
            note = encrypter("Note".toByteArray())
            extensions = encryptExtension(
                mapOf(
                    "whatever " to setOf("Some card"),
                    "you like" to setOf("+12345678")
                )
            )
        }
        val site2 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description2".toByteArray())
            id = 2
            website = encrypter("Website".toByteArray())
            username = encrypter("Username".toByteArray())
            password = encrypter("Password".toByteArray())
            note = encrypter("Note".toByteArray())
            extensions = encryptExtension(
                mapOf(
                    "what" to setOf("Some card2"),
                    "ever" to setOf("+123456780"),
                    "goes" to setOf("a@b")
                )
            )
        }
        val cat = DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = encrypter("Category".toByteArray())
        }
        val lst = mutableListOf(site1, site2)

        // TODO: NO MOCK HERE :(
        //DataModel._categories[cat] = lst

        val model = EditingSiteEntryViewModel()
        model.editSiteEntry(site1)
        SiteEntryView(model, skipForPreviewToWork = true)
    }
}