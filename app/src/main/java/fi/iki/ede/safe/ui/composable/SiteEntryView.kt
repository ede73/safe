package fi.iki.ede.safe.ui.composable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.decrypt
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.R
import fi.iki.ede.safe.clipboard.ClipboardUtils
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.DecryptableSiteEntry
import fi.iki.ede.safe.model.SiteEntryExtensionType
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
import java.time.ZonedDateTime

inline fun <T, C : Collection<T>> C.ifNotEmpty(block: (C) -> Unit): C {
    if (this.isNotEmpty()) block(this)
    return this
}

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
                ).encrypt(encrypter)
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
                enabled = !TextUtils.isEmpty(website) && Uri.parse(website) != null
            ) { Text(stringResource(id = R.string.password_entry_visit)) }
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
            SafeButton(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.username.decrypt(decrypter))
            }) { Text(stringResource(id = R.string.password_entry_username_label)) }
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
            SafeButton(onClick = {
                ClipboardUtils.addToClipboard(context, passEntry.password.decrypt(decrypter))
            }) { Text(stringResource(id = R.string.password_entry_password_label)) }
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
                breachCheckButton(PluginName.HIBP, context, passEntry.password)()
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
                .testTag(TestTag.SITE_ENTRY_NOTE),
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
fun SiteEntryExtensionList(
    viewModel: EditingSiteEntryViewModel,
) {
    // TODO: NONO..flow!
    val allExtensions = DataModel.getAllSiteEntryExtensions()

    SiteEntryExtensionType.entries.sortedBy { it.name }.forEach {
        Column {
            SiteEntryExtensionSelector(
                viewModel,
                allExtensions.getOrDefault(it, emptySet()),
                it
            )
        }
    }
}

@Composable
fun SiteEntryExtensionSelector(
    viewModel: EditingSiteEntryViewModel,
    allKnownValues: Set<String>,
    extensionType: SiteEntryExtensionType,
) {
    val entry by viewModel.editableSiteEntryState.collectAsState()

    fun addToMap(
        map: Map<SiteEntryExtensionType, Set<String>>,
        type: SiteEntryExtensionType,
        value: String
    ): Map<SiteEntryExtensionType, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.plus(value) ?: setOf(value)
        return mutableMap.toMap()
    }

    fun removeFromMap(
        map: Map<SiteEntryExtensionType, Set<String>>,
        type: SiteEntryExtensionType,
        value: String
    ): Map<SiteEntryExtensionType, Set<String>> {
        val mutableMap = map.toMutableMap()
        mutableMap[type] = mutableMap[type]?.minus(value) ?: emptySet()
        return mutableMap
    }

    val allKnownEntries =
        remember { mutableStateListOf<String>().also { it.addAll(allKnownValues) } }
    var checked by remember { mutableStateOf(false) }
    if (!entry.extensions.containsKey(extensionType)) {
        entry.extensions = entry.extensions.toMutableMap().apply {
            this[extensionType] = setOf()
        }
    }
    var selectedEntry by remember { mutableStateOf("") }

    if (entry.extensions[extensionType]!!.isEmpty() && !checked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = !checked },
                modifier = Modifier.testTag(TestTag.SITE_ENTRY_EXTENSION_ENTRY_CHECKBOX)
            )
            Text(text = extensionType.name)
        }
    } else {
        Text(text = extensionType.name)
        EditableComboBox(
            selectedItems = entry.extensions[extensionType]!!.toSet(),
            allItems = allKnownEntries.toSet(),
            onItemSelected = { selectedItem ->
                val currentExtension = entry.extensions
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
                    entry.extensions,
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
                    entry.extensions[extensionType].let { currentSet ->
                        if (itemToDelete in currentSet!!) {
                            viewModel.updateExtensions(
                                removeFromMap(
                                    entry.extensions,
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
    plugin: PluginName,
    context: Context,
    encryptedPassword: IVCipherText
): @Composable () -> Unit = {
    PluginManager.getComposableInterface(plugin)?.getComposable(context, encryptedPassword)
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
            extensions.getOrPut(SiteEntryExtensionType.PAYMENTS) { mutableSetOf() }.add("Some card")
            extensions.getOrPut(SiteEntryExtensionType.PHONE_NUMBERS) { mutableSetOf() }
                .add("+12345678")
//            extensions.getOrPut(SiteEntryExtensionType.EMAILS) { mutableSetOf() }.add("a&b")
        }
        val site2 = DecryptableSiteEntry(1).apply {
            description = encrypter("Description2".toByteArray())
            id = 2
            website = encrypter("Website".toByteArray())
            username = encrypter("Username".toByteArray())
            password = encrypter("Password".toByteArray())
            note = encrypter("Note".toByteArray())
            extensions.getOrDefault(SiteEntryExtensionType.PAYMENTS, mutableSetOf())
                .add("Some card2")
            extensions.getOrDefault(SiteEntryExtensionType.PHONE_NUMBERS, mutableSetOf())
                .add("+123456780")
            extensions.getOrDefault(SiteEntryExtensionType.EMAILS, mutableSetOf())
                .add("a@b2")
        }
        val cat = DecryptableCategoryEntry().apply {
            id = 1
            encryptedName = encrypter("Category".toByteArray())
        }
        val lst = mutableListOf(site1, site2)
        DataModel._categories[cat] = lst

        val model = EditingSiteEntryViewModel()
        model.editSiteEntry(site1)
        SiteEntryView(model, skipForPreviewToWork = true)
    }
}