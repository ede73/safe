package fi.iki.ede.safe

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.preference.PreferenceManager
import com.google.android.play.core.splitcompat.SplitCompatApplication
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.autolock.AutolockingService
import fi.iki.ede.clipboardutils.ClipboardUtils
import fi.iki.ede.db.DBHelper
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.logger.Logger
import fi.iki.ede.logger.firebaseInitialize
import fi.iki.ede.notifications.ConfiguredNotifications
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.preferences.Preferences.PREFERENCE_EXPERIMENTAL_FEATURES
import fi.iki.ede.safe.model.LoginHandler
import fi.iki.ede.safe.notifications.prepareNotifications
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.splits.PluginManager.reinitializePlugins
import fi.iki.ede.safe.splits.PluginName
import fi.iki.ede.safe.splits.getEnabledExperiments
import fi.iki.ede.safe.ui.activities.LoginScreen

private val TAG = "SafeApplication"

class SafeApplication : SplitCompatApplication(), CameraXConfig.Provider,
    SharedPreferences.OnSharedPreferenceChangeListener {
    init {
        instance = this
        Logger.w(TAG, "init")
//        if (BuildConfig.DEBUG) {
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy.Builder()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .build()
//            )
//            StrictMode.enableDefaults()
//        }
        Thread.setDefaultUncaughtExceptionHandler(
            Thread.getDefaultUncaughtExceptionHandler()
                ?.let { MyExceptionHandler(it) })
    }

    override fun onCreate() {
        super.onCreate()
        Logger.w(TAG, "onCreate")
        firebaseInitialize(
            this,
            BuildConfig.GIT_COMMIT_HASH,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
//        throw RuntimeException("Test Crash")
        Preferences.initialize(this)

        // TODO: replace with DI once KMP transform done
        AutolockingFeaturesImpl.registerCallbacks({ context ->
            lockTheApplication(context)
        }, { context ->
            IntentManager.startLoginScreen(context, openCategoryScreenAfterLogin = false)
        }, { context, siteEntryID ->
            IntentManager.startEditSiteEntryScreen(context, siteEntryID)
        }, {
            LoginHandler.isLoggedIn()
        }, { componentActivity ->
            componentActivity is LoginScreen
        }
        )
        DBHelperFactory.initializeDatabase(
            DBHelper(
                this,
                DBHelper.DATABASE_NAME,
                true,
                GPMDB::getExternalTables,
                GPMDB::upgradeTables,
            )
        )
        reinitializePlugins(this)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        ConfiguredNotifications.notifications = prepareNotifications()

    }

    override fun onTerminate() {
        super.onTerminate()
        Logger.w(TAG, "onTerminate")
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        private var instance: SafeApplication? = null
        fun lockTheApplication(context: Context) {
            // Clear the clipboard, if it contains the last password used
            ClipboardUtils.clearClipboard(context)
            // Basically sign out
            LoginHandler.logout()
            AutolockingService.stopAutolockingService(context)
        }
    }

    // We want to ensure as plugins are loaded/unloaded their registration is correctly removed
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREFERENCE_EXPERIMENTAL_FEATURES) {
            val enabledExperiments = Preferences.getEnabledExperiments()
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
