package fi.iki.ede.notifications

import android.app.NotificationManager

data class NotificationSetup(
    val notificationID: Int,
    val channel: String,
    val channelName: Int,
    val channelDescription: Int,
    val category: String,
    val activityToStartOnClick: Class<*>,
    val icon: Int,
    val importance: Int = NotificationManager.IMPORTANCE_LOW
)
