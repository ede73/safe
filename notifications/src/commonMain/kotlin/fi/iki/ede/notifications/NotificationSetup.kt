package fi.iki.ede.notifications

data class NotificationSetup(
    val notificationID: Int,
    val channel: String,
    val channelName: Int,
    val channelDescription: Int,
    val category: String,
    val activityToStartOnClick: Class<*>,
    val icon: Int,
    val importance: NotificationImportance = NotificationImportance.Low // NotificationManager.IMPORTANCE_LOW
)

enum class NotificationImportance {
    Low, High
}