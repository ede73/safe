package fi.iki.ede.notifications

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
private var appContext: Context? = null

fun setNotificationsContext(context: Context) {
    appContext = context.applicationContext
}

fun getNotificationsContext(): Context? = appContext

fun showToast(resId: Int, duration: ToastDuration = ToastDuration.SHORT) {
    appContext?.let {
        showToast(it.getString(resId), duration)
    }
}
