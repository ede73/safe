package fi.iki.ede.autolock

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import fi.iki.ede.logger.Logger
import fi.iki.ede.notifications.ConfiguredNotifications
import fi.iki.ede.notifications.MainNotification
import fi.iki.ede.preferences.Preferences
import java.time.Duration
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ExperimentalTime

private const val TAG = "AutolockingService"

// TODO: BUG: (minor) If you change the lockout time in prefs, it updates only after app restart
@ExperimentalTime
class AutolockingService : Service() {
    private var autoLockCountdownNotifier: CountDownTimer? = null
    private lateinit var mIntentReceiver: BroadcastReceiver
    private lateinit var autoLockNotification: MainNotification

    @ExperimentalAtomicApi
    private val paused: AtomicBoolean = AtomicBoolean(false)

    override fun onBind(intent: Intent): IBinder {
        return LocalBinder()
    }

    @ExperimentalAtomicApi
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
        autoLockNotification =
            MainNotification(this, ConfiguredNotifications.get("autolock_notification"))
    }

    override fun onDestroy() {
        unregisterReceiver(mIntentReceiver)
        if (mFeatures?.isLoggedIn() == true) {
            lockOut()
        }
        autoLockNotification.clearNotification()
        autoLockCountdownNotifier?.cancel()
    }

    @ExperimentalAtomicApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initializeAutolockCountdownTimer()
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY
    }

    @ExperimentalAtomicApi
    private fun initializeAutolockCountdownTimer() {
        if (mFeatures == null || mFeatures?.isLoggedIn() == false) {
            autoLockNotification.clearNotification()
            autoLockCountdownNotifier?.cancel()
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "Inactivity timer pause reset - no longer logger in")
            }
            paused.store(false)
            return
        }
        if (paused.load()) {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "Inactivity timer paused, wont restart")
            }
            return
        }
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "Restart inactivity timer")
        }
        autoLockCountdownNotifier?.cancel()
        autoLockCountdownNotifier = null
        autoLockNotification.setNotification({ this@AutolockingService }) { mainNotification ->
            mainNotification.notify({ this@AutolockingService }) {
                (it as NotificationCompat.Builder).setProgress(100, 0, false)
            }
        }

        val timeoutUntilStop =
            Preferences.getLockTimeoutDuration().inWholeMilliseconds

        autoLockCountdownNotifier =
            object : CountDownTimer(timeoutUntilStop, Duration.ofSeconds(10L).toMillis()) {
                override fun onTick(millisUntilFinished: Long) {
                    // doing nothing.
                    millisecondsTillAutoLock = millisUntilFinished
                    if (mFeatures?.isLoggedIn() == true) {
                        autoLockNotification.setNotification(
                            { this@AutolockingService }
                        ) { mainNotification ->
                            mainNotification.notify({ this@AutolockingService }) {
                                (it as NotificationCompat.Builder).setProgress(
                                    timeoutUntilStop.toInt(),
                                    millisUntilFinished.toInt(), false
                                )
                            }
                        }
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
        mFeatures?.lockApplication(this)
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
    @ExperimentalAtomicApi
    private fun restartTimer() {
        // must be started with startTimer first.
        autoLockCountdownNotifier?.cancel()
        initializeAutolockCountdownTimer()
    }

    @ExperimentalAtomicApi
    private fun pauseTimer() {
        // must be started with startTimer first.
        paused.store(true)
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "Pause inactivity timer")
        }
        autoLockCountdownNotifier?.cancel()
    }

    @ExperimentalAtomicApi
    private fun resumeTimer() {
        paused.store(false)
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "Resume inactivity timer")
        }
        restartTimer()
    }

    var mFeatures: AutoLockingFeatures? = null

    inner class LocalBinder : Binder() {
        @ExperimentalAtomicApi
        fun setAutolockingFeatures(features: AutoLockingFeatures) {
            mFeatures = features
            initializeAutolockCountdownTimer()
        }
    }

    companion object {
        fun startAutolockingService(
            activity: Activity,
            features: AutoLockingFeatures,
            applicationContext: Context
        ): ServiceConnection {
            activity.startService(Intent(applicationContext, AutolockingService::class.java))

            val serviceConnection = object : ServiceConnection {
                @ExperimentalAtomicApi
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as LocalBinder
                    binder.setAutolockingFeatures(features)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Handle disconnection
                }
            }
            val serviceIntent = Intent(applicationContext, AutolockingService::class.java)
            activity.bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
            return serviceConnection
        }

        fun stopAutolockingService(context: Context) {
            context.stopService(Intent(context, AutolockingService::class.java))
        }

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