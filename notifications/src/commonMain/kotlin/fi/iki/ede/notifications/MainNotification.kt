package fi.iki.ede.notifications

expect class MainNotification {
    fun clearNotification()
    fun setNotification(
        getContext: () -> Any, // TODO: Horrible
        customSetup: ((mainNotification: MainNotification) -> Unit)? = null
    )

    fun notify(
        getContext: () -> Any, // TODOL Horrible
        augmentNotificationBuilder: (Any) -> Unit = {} // TODO: And ditto
    )
}
