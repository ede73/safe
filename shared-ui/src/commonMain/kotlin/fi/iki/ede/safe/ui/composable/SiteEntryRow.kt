package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.safe.ui.dialogs.DeleteSiteEntryDialog
import fi.iki.ede.safe.ui.dialogs.MoveSiteEntryDialog
import fi.iki.ede.theme.SafeListItem
import fi.iki.ede.dateutils.DateUtils
import fi.iki.ede.safe.ui.composable.getString
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.crypto.IVCipherText

@OptIn(ExperimentalFoundationApi::class, kotlin.time.ExperimentalTime::class)
@Composable
fun SiteEntryRow(
    siteEntry: DecryptableSiteEntry,
    categoriesState: List<DecryptableCategoryEntry>,
    onSiteEntryClick: (DecryptableSiteEntry) -> Unit,
    onDeleteSiteEntry: (DecryptableSiteEntry) -> Unit,
    modifier: Modifier = Modifier,
    onMoveSiteEntry: ((DecryptableSiteEntry, DecryptableCategoryEntry) -> Unit)? = null
) {
    val displayDeleteDialog = remember { mutableStateOf(false) }
    val displayMenu = remember { mutableStateOf(false) }
    val displayMoveDialog = remember { mutableStateOf(false) }

    SafeListItem(
        borderColor = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    onSiteEntryClick(siteEntry)
                },
                onLongClick = {
                    displayMenu.value = true
                }
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = siteEntry.cachedPlainDescription,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                )

                if (siteEntry.passwordChangedDate != null) {
                    Text(
                        text = getPasswordAgePlurality(
                            DateUtils.getPeriodBetweenDates(
                                siteEntry.passwordChangedDate!!
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = displayMenu.value,
            onDismissRequest = { displayMenu.value = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(text = getString("password_list_delete_password", siteEntry.cachedPlainDescription))
                },
                onClick = {
                    displayMenu.value = false
                    displayDeleteDialog.value = true
                }
            )
            if (onMoveSiteEntry != null) {
                DropdownMenuItem(
                    text = {
                        Text(text = getString("password_list_move_password", siteEntry.cachedPlainDescription))
                    },
                    onClick = {
                        displayMenu.value = false
                        displayMoveDialog.value = true
                    }
                )
            }
        }
        if (displayDeleteDialog.value) {
            DeleteSiteEntryDialog(
                siteEntry = siteEntry,
                onConfirm = {
                    onDeleteSiteEntry(siteEntry)
                    displayDeleteDialog.value = false
                },
                onDismiss = {
                    displayDeleteDialog.value = false
                }
            )
        }
        if (displayMoveDialog.value && onMoveSiteEntry != null) {
            val filteredCategories = categoriesState.filterNot { it.id == siteEntry.categoryId!! }
                .sortedBy { it.plainName.lowercase() }

            MoveSiteEntryDialog(
                targetCategories = filteredCategories,
                onConfirm = { newCategory ->
                    onMoveSiteEntry(siteEntry, newCategory)
                    displayMoveDialog.value = false
                },
                onDismiss = {
                    displayMoveDialog.value = false
                }
            )
        }
    }
}

@Preview
@Composable
fun SiteEntryRowPreview() {
    val site1 = DecryptableSiteEntry(1).apply {
        description = IVCipherText(byteArrayOf(), "Google Account".encodeToByteArray())
    }
    val cat = DecryptableCategoryEntry().apply {
        id = 1
        encryptedName = IVCipherText(byteArrayOf(), "Personal".encodeToByteArray())
    }
    SiteEntryRow(
        siteEntry = site1,
        categoriesState = listOf(cat),
        onSiteEntryClick = {},
        onDeleteSiteEntry = {}
    )
}
