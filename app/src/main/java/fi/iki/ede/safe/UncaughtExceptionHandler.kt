package fi.iki.ede.safe

import fi.iki.ede.logger.Logger
import fi.iki.ede.preferences.Preferences

private val TAG = "MyExceptionHandler"

class MyExceptionHandler(private val defaultHandler: Thread.UncaughtExceptionHandler) :
    Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        // Log the exception to Crashlytics - shouldn't be needed though
        // FirebaseCrashlytics.getInstance().recordException(exception)

        if (BuildConfig.DEBUG) {
            Logger.e(TAG, "Uncaught exception", exception)
        }
        // Clear all plugins
        try {
            Preferences.clearAllPlugins()
        } catch (ex: Exception) {
            // nothing to do here
        }
        defaultHandler.uncaughtException(thread, exception)
    }
}
