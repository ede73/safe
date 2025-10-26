package fi.iki.ede.logger

expect class FirebaseTry<T>(message: String? = null, block: () -> T) {
    fun firebaseCatch(catchBlock: (Throwable) -> T): T
}

// used for test crashing the app from preferences, do not assume any other use case
expect fun firebaseCollectCrashlytics(enabled: Boolean)

expect fun firebaseInitialize(
    commitHash: String, versionName: String, versionCode: Int
)

expect fun <T> firebaseTry(message: String? = null, block: () -> T): FirebaseTry<T>

expect fun <T> firebaseJustTry(message: String? = null, block: () -> T): T?

expect fun firebaseLog(message: String)

expect fun firebaseLog(tag: String, message: String)

expect fun firebaseRecordException(t: Throwable)

expect fun firebaseRecordException(message: String, t: Throwable)
