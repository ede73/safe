package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.iki.ede.gpm.model.SavedGPM
import fi.iki.ede.safe.R
import fi.iki.ede.safe.gpm.ui.composables.ShowGPMInfo
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.testTag
import fi.iki.ede.safe.ui.theme.SafeListItem
import fi.iki.ede.safe.ui.theme.SafeTextButton

@Composable
fun ShowLinkedGPMs(
    gpms: Set<SavedGPM>, onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = { onDismiss() },
    confirmButton = {},
    dismissButton = {
        SafeTextButton(onClick = onDismiss) {
            Text("OK")
        }
    },
    title = { Text(stringResource(id = R.string.google_password_links)) },
    text = {
        var showGpm by remember { mutableStateOf<SavedGPM?>(null) }
        if (showGpm != null) {
            ShowGPMInfo(showGpm!!, onDismiss = { showGpm = null })
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            gpms.forEach {
                SafeListItem {
                    Text(
                        text = it.cachedDecryptedName,
                        modifier = Modifier
                            .clickable {
                                showGpm = it
                            }
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag(TestTag.CATEGORY_MOVE_ROW)
                    )
                }
            }
        }
    }
)