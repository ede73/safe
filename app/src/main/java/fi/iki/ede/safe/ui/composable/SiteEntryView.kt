package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.Password
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.R
import fi.iki.ede.safe.clipboard.ClipboardUtils
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.password.PG_SYMBOLS
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.splits.PluginManager
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.dialogs.ShowLinkedGpmsDialog
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeButton
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AvertInactivityDuringLongTask
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
            DatePicker(
                zonedDateTime = passEntry.passwordChangedDate,
                onValueChange = { date: ZonedDateTime? ->
                    viewModel.updatePasswordChangedDate(date)
                })
            passEntry.id?.let { pid ->
                val gpms = DataModel.getLinkedGPMs(pid)
                gpms.ifNotEmpty {
                    Box(modifier = Modifier.clickable {
                        showLinkedInfo = gpms
                    }) {
                        Text(text = "Has ${it.size} linked GPMs")
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
                (context as AvertInactivityDuringLongTask),
                photo = passEntry.plainPhoto,
                onBitmapCaptured = {
                    val samePhoto =
                        it?.sameAs(passEntry.plainPhoto) ?: (passEntry.plainPhoto == null)
                    if (!samePhoto) {
                        viewModel.updatePhoto(it)
                    }
                })
        }
        if (showLinkedInfo != null) {
            ShowLinkedGpmsDialog(showLinkedInfo!!, onDismiss = { showLinkedInfo = null })
        }
    }
}

@Composable
fun PopCustomPasswordDialog(
    onDismiss: (password: Password?) -> Unit
) {
    val upperCases = remember { mutableStateOf(true) }
    val lowerCases = remember { mutableStateOf(true) }
    val numbers = remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(PG_SYMBOLS) }
    var passwordLength by remember { mutableIntStateOf(18) }
    var regenerate by remember { mutableIntStateOf(0) }
    val password by remember(
        upperCases.value,
        lowerCases.value,
        numbers.value,
        passwordLength,
        symbols,
        regenerate
    ) {
        mutableStateOf(
            PasswordGenerator.genPassword(
                passUpper = upperCases.value,
                passLower = lowerCases.value,
                passNum = numbers.value,
                passSymbols = symbols.isNotEmpty(),
                symbols = symbols,
                length = passwordLength
            )
        )
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss(Password(password))
        },
        title = { Text(stringResource(id = R.string.action_bar_generate_custom_password)) },
        text = {
            Column {
                TextualCheckbox(
                    initiallyChecked = upperCases,
                    textResourceId = R.string.site_entry_uppercases,
                    checkedChanged = { upperCases.value = it })
                TextualCheckbox(
                    initiallyChecked = lowerCases,
                    textResourceId = R.string.site_entry_lowercases,
                    checkedChanged = { lowerCases.value = it })
                TextualCheckbox(
                    initiallyChecked = numbers,
                    textResourceId = R.string.site_entry_numbers,
                    checkedChanged = { numbers.value = it })
                TextField(value = symbols,
                    onValueChange = { newSymbolCandidates ->
                        val filtered =
                            newSymbolCandidates.filter { !it.isLetterOrDigit() && !it.isWhitespace() }
                        val uniqueSymbols = filtered.toSet().joinToString("")
                        symbols = uniqueSymbols
                    })
                Row {
                    Text(passwordLength.toString())
                    Slider(
                        value = passwordLength.toFloat(),
                        onValueChange = { passwordLength = it.toInt() },
                        valueRange = 8.0f..30.0f,
                        steps = 22
                    )
                }
                SafeButton(onClick = { regenerate++ }) {
                    Text(stringResource(id = R.string.site_entry_regenerate))
                }
                PasswordTextField(
                    textTip = R.string.site_entry_generated_password,
                    inputValue = password,
                    enableZoom = true
                )
            }
        },
        confirmButton = {
            SafeButton(
                onClick = {
                    onDismiss(Password(password))
                }
            ) { Text(stringResource(id = R.string.generic_ok)) }
        },
        dismissButton = {
            SafeButton(onClick = { }) {
                Text(stringResource(id = R.string.generic_cancel))
            }
        }
    )
}

@Composable
fun SiteEntryExtensionList(
    viewModel: EditingSiteEntryViewModel,
) {
    // TODO: NONO..flow!
    val allExtensions = DataModel.getAllSiteEntryExtensions()

    VerticalCollapsible(stringResource(id = R.string.site_entry_extension_collapsible)) {
        Preferences.getAllExtensions().sortedBy { it }.forEach {
            Column {
                SiteEntryExtensionSelector(
                    viewModel,
                    allExtensions.getOrDefault(it, emptySet()),
                    it
                )
            }
        }
    }
}

@Composable
fun SiteEntryExtensionSelector(
    viewModel: EditingSiteEntryViewModel,
    allKnownValues: Set<String>,
    extensionType: String,
) {
    val entry by viewModel.editableSiteEntryState.collectAsState()

    fun addToMap(
        map: Map<String, Set<String>>,
        type: String,
        value: String
    ): Map<String, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.plus(value) ?: setOf(value)
        return mutableMap.toMap()
    }

    fun removeFromMap(
        map: Map<String, Set<String>>,
        type: String,
        value: String
    ): Map<String, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.minus(value) ?: emptySet()
        return mutableMap
    }

    val allKnownEntries =
        remember { mutableStateListOf<String>().also { it.addAll(allKnownValues) } }
    var checked by remember { mutableStateOf(false) }
    if (!entry.plainExtension.containsKey(extensionType)) {
        entry.plainExtension = entry.plainExtension.toMutableMap().apply {
            this[extensionType] = setOf()
        }
    }
    var selectedEntry by remember { mutableStateOf("") }

    if (entry.plainExtension[extensionType]!!.isEmpty() && !checked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = !checked },
                modifier = Modifier.testTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY_CHECKBOX)
            )
            Text(text = extensionType)
        }
    } else {
        Text(text = extensionType)
        EditableComboBox(
            selectedItems = entry.plainExtension[extensionType]!!.toSet(),
            allItems = allKnownEntries.toSet(),
            onItemSelected = { selectedItem ->
                val currentExtension = entry.plainExtension
                val currentSet = currentExtension[extensionType] ?: emptySet()
                if (selectedItem in currentSet) {
                    viewModel.updateExtensions(
                        removeFromMap(
                            currentExtension,
                            extensionType,
                            selectedItem
                        )
                    )
                    ""
                } else {
                    viewModel.updateExtensions(
                        addToMap(
                            currentExtension,
                            extensionType,
                            selectedItem
                        )
                    )
                    selectedItem
                }
            },
            onItemEdited = { editedItem ->
                val rem = removeFromMap(
                    entry.plainExtension,
                    extensionType,
                    selectedEntry
                )
                val add = addToMap(rem, extensionType, editedItem)
                viewModel.updateExtensions(add)
                selectedEntry = editedItem
            },
            onItemRequestedToDelete = { itemToDelete ->
                // ONLY can delete if NOT used anywhere else
                if (!DataModel.getAllSiteEntryExtensions(entry.id).flatMap { it.value }.toSet()
                        .contains(itemToDelete)
                ) {
                    // deletion allowed, not used anywhere else..will "autodelete" from full collection on save
                    entry.plainExtension[extensionType].let { currentSet ->
                        if (itemToDelete in currentSet!!) {
                            viewModel.updateExtensions(
                                removeFromMap(
                                    entry.plainExtension,
                                    extensionType,
                                    itemToDelete
                                ),
                            )
                        }
                    }
                    allKnownEntries.remove(itemToDelete)
                }
            })
    }
}

@Composable
fun breachCheckButton(
    context: Context,
    encryptedPassword: IVCipherText
): @Composable () -> Unit = {
    if (PluginManager.isPluginEnabled(PluginName.HIBP))
        PluginManager.getComposableInterface(PluginName.HIBP)
            ?.getComposable(context, encryptedPassword)
            ?.invoke()
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