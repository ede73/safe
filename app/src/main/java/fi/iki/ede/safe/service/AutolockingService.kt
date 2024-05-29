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
import androidx.annotation.RequiresApi
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.ui.activities.AutolockingBaseComponentActivity.Companion.lockTheApplication
import java.time.Duration


// TODO: BUG: (minor) If you change the lockout time in prefs, it updates only after app restart
class AutolockingService : Service() {
    private var autoLockCountdownNotifier: CountDownTimer? = null
    private lateinit var mIntentReceiver: BroadcastReceiver
    private lateinit var serviceNotification: ServiceNotification

    override fun onCreate() {
        mIntentReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (Preferences.getLockOnScreenLock(context, true)) {
                            lockOut()
                        }
                    }

                    ACTION_RESTART_TIMER -> restartTimer()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            newRegisterReceiver()
        } else {
            oldRegisterReceiver()
        }
        serviceNotification = ServiceNotification(this)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun newRegisterReceiver() {
        registerReceiver(
            mIntentReceiver,
            IntentFilter(ACTION_RESTART_TIMER).let {
                it.addAction(Intent.ACTION_SCREEN_OFF)
                it
            }, RECEIVER_NOT_EXPORTED
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun oldRegisterReceiver() {
        registerReceiver(
            mIntentReceiver,
            IntentFilter(ACTION_RESTART_TIMER).let {
                it.addAction(Intent.ACTION_SCREEN_OFF)
                it
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initializeAutolockCountdownTimer()
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(mIntentReceiver)
        if (LoginHandler.isLoggedIn()) {
            lockOut()
        }
        serviceNotification.clearNotification()
        autoLockCountdownNotifier?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun lockOut() {
        serviceNotification.clearNotification()
        autoLockCountdownNotifier?.cancel()
        sendRestartTimer(this)
        lockTheApplication(this)
        launchLoginScreen(this)
    }

    private fun initializeAutolockCountdownTimer() {
        if (!LoginHandler.isLoggedIn()) {
            serviceNotification.clearNotification()
            autoLockCountdownNotifier?.cancel()
            return
        }
        autoLockCountdownNotifier?.cancel()
        autoLockCountdownNotifier = null
        serviceNotification.setNotification(this@AutolockingService)

        val timeoutMinutes = Preferences.getLockTimeoutMinutes(this)

        val timeoutUntilStop = Duration.ofMinutes(timeoutMinutes.toLong()).toMillis()

        autoLockCountdownNotifier =
            object : CountDownTimer(timeoutUntilStop, Duration.ofSeconds(10L).toMillis()) {
                override fun onTick(millisUntilFinished: Long) {
                    // doing nothing.
                    millisecondsTillAutoLock = millisUntilFinished
                    if (LoginHandler.isLoggedIn()) {
                        serviceNotification.updateProgress(
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

    /**
     * Restart the CountDownTimer()
     */
    private fun restartTimer() {
        // must be started with startTimer first.
        autoLockCountdownNotifier?.cancel()
        initializeAutolockCountdownTimer()
    }

    private fun launchLoginScreen(context: Context) {
        this.sendBroadcast(Intent(ACTION_LAUNCH_LOGIN_SCREEN).apply {
            setPackage(context.packageName)
        })
    }

    companion object {
        fun sendRestartTimer(context: Context) {
            context.sendBroadcast(Intent(ACTION_RESTART_TIMER).apply {
                setPackage(context.packageName)
            })
        }

        private const val ACTION_RESTART_TIMER = "fi.iki.ede.action.RESTART_TIMER"

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