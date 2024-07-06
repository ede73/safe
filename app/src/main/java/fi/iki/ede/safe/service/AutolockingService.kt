package fi.iki.ede.safe.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import fi.iki.ede.crypto.BuildConfig
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.notifications.AutoLockNotification
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity.Companion.lockTheApplication
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AutolockingService"

// TODO: BUG: (minor) If you change the lockout time in prefs, it updates only after app restart
class AutolockingService : Service() {
    private var autoLockCountdownNotifier: CountDownTimer? = null
    private lateinit var mIntentReceiver: BroadcastReceiver
    private lateinit var autoLockNotification: AutoLockNotification
    private val paused: AtomicBoolean = AtomicBoolean(false)

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        mIntentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (Preferences.getLockOnScreenLock(true)) {
                            lockOut()
                        }
                    }

                    ACTION_RESTART_TIMER -> restartTimer()
                    ACTION_PAUSE_TIMER -> pauseTimer()
                    ACTION_RESUME_TIMER -> resumeTimer()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            newRegisterReceiver()
        } else {
            oldRegisterReceiver()
        }
        autoLockNotification = AutoLockNotification(this)
    }

    override fun onDestroy() {
        unregisterReceiver(mIntentReceiver)
        if (LoginHandler.isLoggedIn()) {
            lockOut()
        }
        autoLockNotification.clearNotification()
        autoLockCountdownNotifier?.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initializeAutolockCountdownTimer()
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY
    }

    private fun initializeAutolockCountdownTimer() {
        if (!LoginHandler.isLoggedIn()) {
            autoLockNotification.clearNotification()
            autoLockCountdownNotifier?.cancel()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Inactivity timer pause reset - no longer logger in")
            }
            paused.set(false)
            return
        }
        if (paused.get()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Inactivity timer paused, wont restart")
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Restart inactivity timer")
        }
        autoLockCountdownNotifier?.cancel()
        autoLockCountdownNotifier = null
        autoLockNotification.setNotification(this@AutolockingService)

        val timeoutUntilStop = Preferences.getLockTimeoutDuration().inWholeMilliseconds

        autoLockCountdownNotifier =
            object : CountDownTimer(timeoutUntilStop, Duration.ofSeconds(10L).toMillis()) {
                override fun onTick(millisUntilFinished: Long) {
                    // doing nothing.
                    millisecondsTillAutoLock = millisUntilFinished
                    if (LoginHandler.isLoggedIn()) {
                        autoLockNotification.updateProgress(
                            context = this@AutolockingService,
                            timeoutUntilStop.toInt(),
                            millisecondsTillAutoLock.toInt()
                        )
                    }
                }

                override fun onFinish() {
                    lockOut()
                    millisecondsTillAutoLock = 0
                }
            }.apply {
                start()
            }
        millisecondsTillAutoLock = timeoutUntilStop
    }

    private fun launchLoginScreen(context: Context) {
        this.sendBroadcast(Intent(ACTION_LAUNCH_LOGIN_SCREEN).apply {
            setPackage(context.packageName)
        })
    }

    private fun lockOut() {
        autoLockNotification.clearNotification()
        autoLockCountdownNotifier?.cancel()
        sendRestartTimer(this)
        lockTheApplication(this)
        launchLoginScreen(this)
    }

    private fun getBCastIntentFilter() = IntentFilter(ACTION_RESTART_TIMER).let {
        it.addAction(Intent.ACTION_SCREEN_OFF)
        it.addAction(ACTION_PAUSE_TIMER)
        it.addAction(ACTION_RESUME_TIMER)
        it
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun newRegisterReceiver() = registerReceiver(
        mIntentReceiver,
        getBCastIntentFilter(),
        RECEIVER_NOT_EXPORTED
    )


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun oldRegisterReceiver() = registerReceiver(
        mIntentReceiver,
        getBCastIntentFilter(),
    )


    /**
     * Restart the CountDownTimer()
     */
    private fun restartTimer() {
        // must be started with startTimer first.
        autoLockCountdownNotifier?.cancel()
        initializeAutolockCountdownTimer()
    }

    private fun pauseTimer() {
        // must be started with startTimer first.
        paused.set(true)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Pause inactivity timer")
        }
        autoLockCountdownNotifier?.cancel()
    }

    private fun resumeTimer() {
        paused.set(false)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Resume inactivity timer")
        }
        restartTimer()
    }

    companion object {
        fun sendRestartTimer(context: Context) =
            context.sendBroadcast(Intent(ACTION_RESTART_TIMER).apply {
                setPackage(context.packageName)
            })

        fun sendPauseTimer(context: Context) =
            context.sendBroadcast(Intent(ACTION_PAUSE_TIMER).apply {
                setPackage(context.packageName)
            })

        fun sendResumeTimer(context: Context) =
            context.sendBroadcast(Intent(ACTION_RESUME_TIMER).apply {
                setPackage(context.packageName)
            })


        private const val ACTION_RESTART_TIMER = "fi.iki.ede.action.RESTART_TIMER"
        private const val ACTION_PAUSE_TIMER = "fi.iki.ede.action.PAUSE_TIMER"
        private const val ACTION_RESUME_TIMER = "fi.iki.ede.action.RESUME_TIMER"

        // Service cannot start activities on modern android, so to solve the lock -> login screen
        // transition problem, we'll send launch login screen intent that will be listened
        // in AutoLockingAppCompactActivity and AutoLockingComponentActivity
        const val ACTION_LAUNCH_LOGIN_SCREEN = "fi.iki.ede.action.LAUNCH_LOGIN_SCREEN"

        /**
         * @return time remaining in milliseconds before auto lock
         */
        var millisecondsTillAutoLock: Long = 0
            private set
    }
}