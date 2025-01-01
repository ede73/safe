package fi.iki.ede.safe.autolocking

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity


@Suppress("LeakingThis")
open class AutoLockingBaseAppCompatActivity(features: AutoLockingFeatures) : AppCompatActivity(),
    ScreenOffLocker {

    override val mIntentReceiver = screenOffIntentReceiver
    override val mFeatures = features

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        doOnCreate("AutoLockingAppCompactActivity created")
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