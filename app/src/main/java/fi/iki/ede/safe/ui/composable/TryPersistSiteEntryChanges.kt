package fi.iki.ede.safe.ui.composable

import android.text.TextUtils
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.theme.SafeButton
import kotlin.time.ExperimentalTime

@Composable
@ExperimentalTime
fun TryPersistSiteEntryChanges(
    edits: EditableSiteEntry,
    passwordChanged: Boolean,
    onDismiss: () -> Unit,
    onSaved: (Boolean) -> Unit
) {
    val emptyDescription = remember { mutableStateOf(false) }
    // test for empty
    if (TextUtils.isEmpty(edits.description)) {
        emptyDescription.value = true
    }
    if (emptyDescription.value) {
        AlertDialog(
            onDismissRequest = {
                onDismiss()
                emptyDescription.value = false
            },
            confirmButton = {
                SafeButton(onClick = {
                    onDismiss()
                    emptyDescription.value = false
                }) {
                    Text(text = stringResource(id = R.string.generic_ok))
                }
            },
            title = { Text(text = stringResource(id = R.string.password_entry_description_required)) })
        return
    }

    PersistPasswordEntryChanges(
        edits,
        passwordChanged,
        onSaved
    )
}