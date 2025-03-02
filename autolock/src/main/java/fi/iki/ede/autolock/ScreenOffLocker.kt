package fi.iki.ede.autolock

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import fi.iki.ede.preferences.Preferences

interface ScreenOffLocker : AvertInactivityDuringLongTask {
    val mIntentReceiver: BroadcastReceiver
    val mFeatures: AutoLockingFeatures

    val screenOffIntentReceiver: BroadcastReceiver
        get() = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Actual intent.action checked in the function below
                checkScreenOff(context, intent)
            }
        }

    // used during long tasks - to keep timer from going off
    // not perfect, just resets, doesn't actually pause(and resume)
    override fun avertInactivity(context: Context, why: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Try to restart inactivity timer because $why")
        }
        AutolockingService.sendRestartTimer(context)
    }

    override fun pauseInactivity(context: Context, why: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Pause inactivity timer because $why")
        }
        AutolockingService.sendPauseTimer(context)
    }

    override fun resumeInactivity(context: Context, why: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Resume inactivity timer because $why")
        }
        AutolockingService.sendResumeTimer(context)
    }

    private fun checkScreenOff(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (Preferences.getLockOnScreenLock(true)) {
                    // TODO:
                    mFeatures.lockApplication(context)
                }
            }

            AutolockingService.ACTION_LAUNCH_LOGIN_SCREEN -> {
                mFeatures.startLoginScreen(context)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerBroadcastReceiver(context: Context, intentReceiver: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                intentReceiver, IntentFilter().let {
                    it.addAction(Intent.ACTION_SCREEN_OFF)
                    it.addAction(AutolockingService.ACTION_LAUNCH_LOGIN_SCREEN)
                    it
                }, Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                intentReceiver, IntentFilter().let {
                    it.addAction(Intent.ACTION_SCREEN_OFF)
                    it.addAction(AutolockingService.ACTION_LAUNCH_LOGIN_SCREEN)
                    it
                }
            )
        }
    }

    // If we're not logged in (due to inactivity - or what ever)
    // always launch login screen
    fun checkShouldLaunchLoginScreen(context: Context): Boolean {
        if (mFeatures.isLoggedIn()) {
            return false
        }
        if (!mFeatures.isThisLoginScreen(this as ComponentActivity)) {
            mFeatures.startLoginScreen(context)
        }
        return true
    }

    fun maybeRestartInactivityOnTouch(context: Context, event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            avertInactivity(context, "Touch down")
        }
    }

    fun Activity.doOnCreate(why: String) {
        avertInactivity(this, why)
    }

    fun Activity.doOnResume() {
        if (checkShouldLaunchLoginScreen(this)) {
            return
        }

        registerBroadcastReceiver(this, mIntentReceiver)
    }

    fun Activity.doOnStop() {
        try {
            unregisterReceiver(mIntentReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun Activity.doOnDispatchTouchEvent(event: MotionEvent): MotionEvent {
        maybeRestartInactivityOnTouch(this, event)
        return event
    }

    companion object {
        const val TAG = "AutoLockingComponentActivity"
    }
}
