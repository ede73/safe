package fi.iki.ede.safe

import fi.iki.ede.safe.model.Preferences

class MyExceptionHandler(private val defaultHandler: Thread.UncaughtExceptionHandler) :
    Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        // Clear all plugins
        try {
            Preferences.clearAllPlugins()
        } catch (ex: Exception) {
            // nothing to do here
        }
        defaultHandler.uncaughtException(thread, exception)
    }
}
