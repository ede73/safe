package fi.iki.ede.logger

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics

internal const val TAG = "fireebasehandler"

actual class FirebaseTry<T> actual constructor(
    private val message: String?,
    private val block: () -> T
) {
    actual fun firebaseCatch(catchBlock: (Throwable) -> T): T {
        return try {
            if (message != null) {
                Firebase.crashlytics.log(message)
                Logger.e(TAG, message)
            }
            block()
        } catch (t: Throwable) {
            Firebase.crashlytics.recordException(t)
            Logger.e(TAG, "${message ?: ""} $t")
            catchBlock(t)
        }
    }
}

// used for test crashing the app from preferences, do not assume any other use case
actual fun firebaseCollectCrashlytics(enabled: Boolean) {
    Firebase.crashlytics.isCrashlyticsCollectionEnabled = enabled
}

// This is the platform-specific part that needs a context
fun firebaseInitialize(
    context: Context, commitHash: String, versionName: String, versionCode: Int
) {
    FirebaseApp.initializeApp(context)
    firebaseInitialize(commitHash, versionName, versionCode)
}

actual fun firebaseInitialize(
    commitHash: String, versionName: String, versionCode: Int
) {
    Firebase.crashlytics.setCustomKey("git_commit_hash", commitHash)
    Firebase.crashlytics.setCustomKey("VERSION_NAME", versionName)
    Firebase.crashlytics.setCustomKey("VERSION_CODE", versionCode)
    Firebase.crashlytics.isCrashlyticsCollectionEnabled = true
}

actual fun <T> firebaseTry(message: String?, block: () -> T): FirebaseTry<T> =
    FirebaseTry(message, block)

actual fun <T> firebaseJustTry(message: String?, block: () -> T): T? {
    return try {
        if (message != null) {
            Firebase.crashlytics.log(message)
            Logger.e(TAG, message)
        }
        block()
    } catch (t: Throwable) {
        Firebase.crashlytics.recordException(t)
        Logger.e(TAG, "${message ?: ""} $t")
        null
    }
}

actual fun firebaseLog(message: String) {
    Firebase.crashlytics.log(message)
    Logger.i(TAG, message)
}

actual fun firebaseLog(tag: String, message: String) {
    Firebase.crashlytics.log("$tag $message")
    Logger.i(tag, message)
}

actual fun firebaseRecordException(t: Throwable) {
    Logger.i(TAG, "Caught exception", t)
    Firebase.crashlytics.recordException(t)
}

actual fun firebaseRecordException(message: String, t: Throwable) {
    firebaseLog(message)
    firebaseRecordException(t)
}
