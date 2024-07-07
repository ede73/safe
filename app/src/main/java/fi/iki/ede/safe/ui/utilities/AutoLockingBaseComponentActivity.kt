package fi.iki.ede.safe.ui.utilities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import fi.iki.ede.safe.clipboard.ClipboardUtils
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.service.AutolockingService

@Suppress("LeakingThis")
open class AutoLockingBaseComponentActivity : ComponentActivity(), ScreenOffLocker {

    override val mIntentReceiver = screenOffIntentReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        doOnCreate("AutoLockingComponentActivity created")
    }

    override fun onResume() {
        super.onResume()
        doOnResume()
    }

    override fun onStop() {
        super.onStop()
        doOnStop()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        super.dispatchTouchEvent(doOnDispatchTouchEvent(event))

    companion object {
        fun lockTheApplication(context: Context) {
            // Clear the clipboard, if it contains the last password used
            ClipboardUtils.clearClipboard(context)
            // Basically sign out
            LoginHandler.logout()
            context.stopService(Intent(context, AutolockingService::class.java))
        }
    }
}