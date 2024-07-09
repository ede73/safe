package fi.iki.ede.safe.ui.composable

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun setupActivityResultLauncher(
    cancelled: ((ActivityResult) -> Unit)? = null,
    resultOk: (ActivityResult) -> Unit
)
        : ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        when (it.resultCode) {
            Activity.RESULT_OK -> {
                resultOk(it)
            }

            Activity.RESULT_CANCELED -> {
                if (cancelled != null)
                    cancelled(it)
            }
        }
    }
}