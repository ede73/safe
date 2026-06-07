@file:OptIn(kotlin.time.ExperimentalTime::class)
package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.theme.SafeListItem

object DesktopSiteEntryNavigation {
    var activeSiteEntry by mutableStateOf<DecryptableSiteEntry?>(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SiteEntryRow(
    siteEntry: DecryptableSiteEntry,
    categoriesState: List<DecryptableCategoryEntry>,
) {
    val db = remember { fi.iki.ede.db.DBHelperFactory.getDBHelper() }
    var displayMenu by remember { mutableStateOf(false) }

    SafeListItem(
        borderColor = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    DesktopSiteEntryNavigation.activeSiteEntry = siteEntry
                },
                onLongClick = {
                    displayMenu = true
                }
            )
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
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = displayMenu,
            onDismissRequest = { displayMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Password") },
                onClick = {
                    displayMenu = false
                    db.hardDeleteSiteEntry(siteEntry.id!!)
                    DesktopNavigation.dbRefreshTrigger++
                }
            )
        }
    }
}
