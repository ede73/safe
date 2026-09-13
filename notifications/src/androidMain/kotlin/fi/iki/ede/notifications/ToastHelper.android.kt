package fi.iki.ede.notifications

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import fi.iki.ede.logger.Logger

actual object ToastHelper {
    private const val TAG = "ToastHelper"

    actual fun showToast(message: String, duration: ToastDuration) {
        val context = getNotificationsContext()
        if (context == null) {
            Logger.w(TAG, "Notification context not initialized; unable to show toast: $message")
            return
        }
        val androidDuration = when (duration) {
            ToastDuration.SHORT -> Toast.LENGTH_SHORT
            ToastDuration.LONG -> Toast.LENGTH_LONG
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, message, androidDuration).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, androidDuration).show()
            }
        }
    }
}
