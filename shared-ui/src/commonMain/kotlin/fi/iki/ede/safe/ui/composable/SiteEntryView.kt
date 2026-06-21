package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.theme.LocalSafeTheme
import fi.iki.ede.theme.SafeButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteEntryView(
    description: String,
    onDescriptionChange: (String) -> Unit,
    website: String,
    onWebSiteChange: (String) -> Unit,
    username: String,
    onUsernameChange: (Password) -> Unit,
    password: String,
    onPasswordChange: (Password) -> Unit,
    note: String,
    onNoteChange: (Password) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    modifier: Modifier = Modifier,
    originalPassword: String? = null,
    customPasswordGeneratorContent: (@Composable () -> Unit)? = null,
    breachCheckButtonContent: (@Composable () -> Unit)? = null,
    datePickerContent: (@Composable () -> Unit)? = null,
    photoContent: (@Composable () -> Unit)? = null,
    extensionsContent: (@Composable () -> Unit)? = null,
    linkedGpmContent: (@Composable () -> Unit)? = null,
    bottomBarContent: (@Composable () -> Unit)? = null
) {
    val hideFocusLine = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val padding = Modifier.padding(6.dp)
    val safeTheme = LocalSafeTheme.current

    Scaffold(
        bottomBar = bottomBarContent ?: {},
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .testTag(TestTag.SITE_ENTRY_DESCRIPTION),
                    colors = hideFocusLine,
                )
            }

            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                SafeButton(
                    onClick = {
                        if (website.isNotBlank()) {
                            onOpenBrowser(website)
                        }
                    },
                    enabled = website.isNotBlank(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Visit website"
                    )
                }
                Spacer(Modifier.weight(1f))
                TextField(
                    value = website,
                    onValueChange = onWebSiteChange,
                    label = { Text("Website") },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .testTag(TestTag.SITE_ENTRY_WEBSITE),
                    colors = hideFocusLine,
                )
            }

            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                SafeButton(
                    onClick = {
                        onCopyToClipboard(username)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy username"
                    )
                }
                Spacer(Modifier.weight(1f))
                PasswordTextField(
                    label = "Username",
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .testTag(TestTag.SITE_ENTRY_USERNAME),
                    inputValue = username,
                    onValueChange = onUsernameChange,
                    highlight = false,
                )
            }

            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                val text = buildAnnotatedString {
                    append("Numbers highlighted in ")
                    withStyle(style = SpanStyle(background = safeTheme.customColors.numbers108652)) {
                        append("blue")
                    }
                    append(", lowercase 'l' in ")
                    withStyle(style = SpanStyle(background = safeTheme.customColors.lettersL)) {
                        append("red")
                    }
                    append(", whitespace in ")
                    withStyle(style = SpanStyle(background = safeTheme.customColors.whiteSpaceL)) {
                        append("yellow")
                    }
                }
                Text(
                    text = text,
                    style = safeTheme.customFonts.smallNote,
                )
            }

            Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (originalPassword != null && password != originalPassword) {
                        SafeButton(
                            onClick = {
                                onCopyToClipboard(originalPassword)
                            }, contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                modifier = Modifier
                                    .padding(0.dp)
                                    .size(20.dp),
                                contentDescription = "Copy original password"
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                modifier = Modifier
                                    .padding(0.dp)
                                    .size(20.dp),
                                contentDescription = "Copy original password"
                            )
                        }
                    }
                    SafeButton(
                        modifier = Modifier.padding(0.dp),
                        onClick = {
                            onCopyToClipboard(password)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            modifier = Modifier
                                .padding(0.dp)
                                .size(20.dp),
                            contentDescription = "Copy password"
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                PasswordTextField(
                    label = "Password",
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .testTag(TestTag.SITE_ENTRY_PASSWORD),
                    inputValue = password,
                    onValueChange = onPasswordChange,
                    textStyle = safeTheme.customFonts.regularPassword,
                    enableZoom = true
                )
            }

            if (breachCheckButtonContent != null) {
                Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                    breachCheckButtonContent()
                }
            }

            if (datePickerContent != null) {
                Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                    datePickerContent()
                }
            }

            if (linkedGpmContent != null) {
                Row(modifier = padding, verticalAlignment = Alignment.CenterVertically) {
                    linkedGpmContent()
                }
            }

            if (extensionsContent != null) {
                extensionsContent()
            }

            PasswordTextField(
                label = "Note",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTag.SITE_ENTRY_NOTE),
                inputValue = note,
                onValueChange = onNoteChange,
                singleLine = false,
                maxLines = 22,
                highlight = false,
            )

            if (photoContent != null) {
                photoContent()
            }
        }
    }
}
