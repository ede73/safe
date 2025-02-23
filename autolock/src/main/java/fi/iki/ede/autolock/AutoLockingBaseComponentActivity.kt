package fi.iki.ede.autolock

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import fi.iki.ede.autolock.NavigationBarHelper.enableDrawingBehindNavigationBar

@Suppress("LeakingThis")
open class AutoLockingBaseComponentActivity(features: AutoLockingFeatures) : ComponentActivity(),
    ScreenOffLocker {

    override val mIntentReceiver = screenOffIntentReceiver
    override val mFeatures = features

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDrawingBehindNavigationBar(window)
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
}