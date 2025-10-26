package fi.iki.ede.logger

internal actual const val TAG = "fireebasehandler"

actual class FirebaseTry<T> actual constructor(private val message: String?, private val block: () -> T) {
    actual fun firebaseCatch(catchBlock: (Throwable) -> T): T = block()
}

actual fun firebaseCollectCrashlytics(enabled: Boolean) {}

actual fun firebaseInitialize(
    commitHash: String, versionName: String, versionCode: Int
) {}

actual fun <T> firebaseTry(message: String?, block: () -> T): FirebaseTry<T> =
    FirebaseTry(message, block)

actual fun <T> firebaseJustTry(message: String?, block: () -> T): T? = block()

actual fun firebaseLog(message: String) {}

actual fun firebaseLog(tag: String, message: String) {}

actual fun firebaseRecordException(t: Throwable) {}

actual fun firebaseRecordException(message: String, t: Throwable) {}
