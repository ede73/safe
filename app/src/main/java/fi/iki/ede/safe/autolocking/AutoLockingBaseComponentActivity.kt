package fi.iki.ede.safe.autolocking

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity

@Suppress("LeakingThis")
open class AutoLockingBaseComponentActivity(features: AutoLockingFeatures) : ComponentActivity(),
    ScreenOffLocker {

    override val mIntentReceiver = screenOffIntentReceiver
    override val mFeatures = features

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
}