package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.composable.BackupComposable
import kotlin.time.ExperimentalTime

@ExperimentalTime
class BackupDatabaseScreen : AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val toast = remember { mutableStateOf("") }
            if (toast.value != "") {
                Toast.makeText(context, toast.value, Toast.LENGTH_LONG).show()
                toast.value = ""
            }
            BackupComposable(toast)
        }
    }
}
