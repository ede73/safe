package fi.iki.ede.safe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.preference.PreferenceManager
import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import fi.iki.ede.preferences.Preferences.PREFERENCE_EXPERIMENTAL_FEATURES
import fi.iki.ede.safe.autolocking.AutolockingService
import fi.iki.ede.safe.db.DBHelper
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginManager.reinitializePlugins
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.getEnabledExperiments

private val TAG = "SafeApplication"

class SafeApplication : SplitCompatApplication(), CameraXConfig.Provider,
    SharedPreferences.OnSharedPreferenceChangeListener {
    init {
        instance = this
        Log.w(TAG, "init")
//        if (BuildConfig.DEBUG) {
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy.Builder()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .build()
//            )
//            StrictMode.enableDefaults()
//        }
        Thread.setDefaultUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()
            ?.let { MyExceptionHandler(it) })
    }

    override fun onCreate() {
        super.onCreate()
        Log.w(TAG, "onCreate")
        FirebaseApp.initializeApp(this)
        Firebase.crashlytics.setCustomKey("git_commit_hash", BuildConfig.GIT_COMMIT_HASH)
        Firebase.crashlytics.setCustomKey("VERSION_NAME", BuildConfig.VERSION_NAME)
        Firebase.crashlytics.setCustomKey("VERSION_CODE", BuildConfig.VERSION_CODE)
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = true
//        throw RuntimeException("Test Crash")
        fi.iki.ede.preferences.Preferences.initialize(this)
        DBHelperFactory.initializeDatabase(DBHelper(this, DBHelper.DATABASE_NAME, true))
        reinitializePlugins(this)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.w(TAG, "onTerminate")
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        private var instance: SafeApplication? = null
        fun lockTheApplication(context: Context) {
            // Clear the clipboard, if it contains the last password used
            fi.iki.ede.clipboardutils.ClipboardUtils.clearClipboard(context)
            // Basically sign out
            LoginHandler.logout()
            AutolockingService.stopAutolockingService(context)
        }
    }

    // We want to ensure as plugins are loaded/unloaded their registration is correctly removed
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREFERENCE_EXPERIMENTAL_FEATURES) {
            val enabledExperiments = fi.iki.ede.preferences.Preferences.getEnabledExperiments()
            PluginName.entries.forEach {
                if (it !in enabledExperiments) {
                    // this plugin might have just been disabled
                    IntentManager.removePluginIntegrations(it)
                }
            }
        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }
}
