package fi.iki.ede.notifications

import fi.iki.ede.logger.Logger

actual object ToastHelper {
    private const val TAG = "ToastHelper"

    actual fun showToast(message: String, duration: ToastDuration) {
        Logger.i(TAG, "iOS Toast [$duration]: $message")
    }
}
