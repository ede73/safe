package fi.iki.ede.logger

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics

internal const val TAG = "fireebasehandler"

class FirebaseTry<T>(private val message: String? = null, val block: () -> T) {
    fun firebaseCatch(catchBlock: (Throwable) -> T): T {
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
fun firebaseCollectCrashlytics(enabled: Boolean) {
    Firebase.crashlytics.isCrashlyticsCollectionEnabled = enabled
}

fun firebaseInitialize(
    context: Context, commitHash: String, versionName: String, versionCode: Int
) {
    FirebaseApp.initializeApp(context)
    Firebase.crashlytics.setCustomKey("git_commit_hash", commitHash)
    Firebase.crashlytics.setCustomKey("VERSION_NAME", versionName)
    Firebase.crashlytics.setCustomKey("VERSION_CODE", versionCode)
    Firebase.crashlytics.isCrashlyticsCollectionEnabled = true
}

fun <T> firebaseTry(message: String? = null, block: () -> T): FirebaseTry<T> =
    FirebaseTry(message, block)

fun <T> firebaseJustTry(message: String? = null, block: () -> T): T? {
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

fun firebaseLog(message: String) {
    Firebase.crashlytics.log(message)
    Logger.i(TAG, message)
}

fun firebaseLog(tag: String, message: String) {
    Firebase.crashlytics.log("$tag $message")
    Logger.i(tag, message)
}

fun firebaseRecordException(t: Throwable) {
    Logger.i(TAG, "Caught exception", t)
    Firebase.crashlytics.recordException(t)
}

fun firebaseRecordException(message: String, t: Throwable) {
    firebaseLog(message)
    firebaseRecordException(t)
}