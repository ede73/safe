package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.safe.ui.sync.ConflictType
import fi.iki.ede.safe.ui.sync.SyncConflict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionDialog(
    conflicts: List<SyncConflict>,
    onDismissRequest: () -> Unit,
    onResolveCompleted: (resolvedConflicts: List<SyncConflict>) -> Unit
) {
    val conflictList = remember { mutableStateListOf(*conflicts.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Sync Conflicts",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Multi-Device Conflict Resolver",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "${conflictList.size} conflicts detected between Local (Android) and Remote (iOS):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    itemsIndexed(conflictList) { index, conflict ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "[${conflict.type.name}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = conflict.conflictId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Case 1: Renamed or Divergent Name
                                if (conflict.localItem != null && conflict.remoteItem != null && conflict.localItem.name != conflict.remoteItem.name) {
                                    Text("Name Selection:", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = conflict.selectedName == conflict.localItem.name,
                                            onClick = { conflict.selectedName = conflict.localItem.name }
                                        )
                                        Text("Local: ${conflict.localItem.name}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = conflict.selectedName == conflict.remoteItem.name,
                                            onClick = { conflict.selectedName = conflict.remoteItem.name }
                                        )
                                        Text("Remote: ${conflict.remoteItem.name}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Case 2: Password Divergence
                                if (conflict.localItem != null && conflict.remoteItem != null && conflict.localItem.password != conflict.remoteItem.password) {
                                    Text("Password Selection:", style = MaterialTheme.typography.labelSmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = conflict.selectedPassword == conflict.localItem.password,
                                            onClick = { conflict.selectedPassword = conflict.localItem.password }
                                        )
                                        Text("Local: ${conflict.localItem.password}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = conflict.selectedPassword == conflict.remoteItem.password,
                                            onClick = { conflict.selectedPassword = conflict.remoteItem.password }
                                        )
                                        Text("Remote: ${conflict.remoteItem.password}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Case 3: Deletion Conflict
                                if (conflict.type == ConflictType.DELETED_ON_ONE_SIDE) {
                                    val localDel = conflict.localItem?.isDeleted == true
                                    Text(
                                        text = if (localDel) "Deleted on Local (Android)" else "Deleted on Remote (iOS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = !conflict.selectedIsDeleted,
                                            onClick = { conflict.selectedIsDeleted = false }
                                        )
                                        Text("Keep Active Record", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = conflict.selectedIsDeleted,
                                            onClick = { conflict.selectedIsDeleted = true }
                                        )
                                        Text("Confirm Deletion", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Case 4: Unmatched New Entry (Added on one side)
                                if (conflict.type == ConflictType.NEW_ENTRY_ADDED) {
                                    val isLocalNew = conflict.localItem != null
                                    Text(
                                        text = if (isLocalNew) "Added on Local: ${conflict.localItem?.name}" else "Added on Remote: ${conflict.remoteItem?.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolveCompleted(conflictList) }
            ) {
                Text("Apply Resolutions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
