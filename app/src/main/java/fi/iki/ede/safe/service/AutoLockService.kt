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
import fi.iki.ede.safe.ui.activities.AutoLockingComponentActivity.Companion.lockTheApplication


// TODO: BUG: (minor) If you change the lockout time in prefs, it updates only after app restart
class AutoLockService : Service() {
    private var t: CountDownTimer? = null
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
        startTimer()
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
        t?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun lockOut() {
        serviceNotification.clearNotification()
        t?.cancel()
        sendRestartTimer(this)
        lockTheApplication(this)
        launchLoginScreen()
    }

    private fun startTimer() {
        if (!LoginHandler.isLoggedIn()) {
            serviceNotification.clearNotification()
            t?.cancel()
            return
        }
        serviceNotification.setNotification(this@AutoLockService)
        val timeoutMinutes = Preferences.getLockTimeoutMinutes(this)

        val timeoutUntilStop = timeoutMinutes * 60000L
        val lt = object : CountDownTimer(timeoutUntilStop, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // doing nothing.
                timeRemaining = millisUntilFinished
                if (LoginHandler.isLoggedIn()) {
                    serviceNotification.updateProgress(
                        timeoutUntilStop.toInt(),
                        timeRemaining.toInt()
                    )
                }
            }

            override fun onFinish() {
                lockOut()
                timeRemaining = 0
            }
        }
        lt.start()
        t = lt
        timeRemaining = timeoutUntilStop
    }

    /**
     * Restart the CountDownTimer()
     */
    private fun restartTimer() {
        // must be started with startTimer first.
        t?.cancel()
        t?.start()
    }

    private fun launchLoginScreen() {
        this.sendBroadcast(Intent(ACTION_LAUNCH_LOGIN_SCREEN))
    }

    companion object {
        fun sendRestartTimer(context: Context) {
            context.sendBroadcast(Intent(ACTION_RESTART_TIMER))
        }

        private const val ACTION_RESTART_TIMER = "fi.iki.ede.action.RESTART_TIMER"

        // Service cannot start activities on modern android, so to solve the lock -> login screen
        // transition problem, we'll send launch login screen intent that will be listened
        // in AutoLockingAppCompactActivity and AutoLockingComponentActivity
        const val ACTION_LAUNCH_LOGIN_SCREEN = "fi.iki.ede.action.LAUNCH_LOGIN_SCREEN"

        /**
         * @return time remaining in milliseconds before auto lock
         */
        var timeRemaining: Long = 0
            private set
    }
}