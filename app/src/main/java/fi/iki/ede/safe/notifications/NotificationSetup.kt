package fi.iki.ede.safe.notifications

import android.app.NotificationManager
import kotlin.reflect.KClass

data class NotificationSetup(
    val notificationID: Int,
    val channel: String,
    val channelName: Int,
    val channelDescription: Int,
    val category: String,
    val type: KClass<*>,
    val icon: Int,
    val importance: Int = NotificationManager.IMPORTANCE_LOW
)
