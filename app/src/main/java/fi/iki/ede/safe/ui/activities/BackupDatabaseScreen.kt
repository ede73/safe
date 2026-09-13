package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.notifications.ToastDuration
import fi.iki.ede.notifications.showToast
import fi.iki.ede.safe.ui.composable.BackupComposable
import kotlin.time.ExperimentalTime

@ExperimentalTime
class BackupDatabaseScreen : AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val toast = remember { mutableStateOf("") }
            if (toast.value != "") {
                showToast(toast.value, ToastDuration.LONG)
                toast.value = ""
            }
            BackupComposable(toast)
        }
    }
}
