package fi.iki.ede.safe.ui.utilities

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

internal const val TAG = "fireebasehandler"

class FirebaseTry<T>(private val message: String? = null, val block: () -> T) {
    fun firebaseCatch(catchBlock: (Throwable) -> T): T {
        return try {
            if (message != null) {
                Firebase.crashlytics.log(message)
                Log.e(TAG, "$message")
            }
            block()
        } catch (t: Throwable) {
            Firebase.crashlytics.recordException(t)
            Log.e(TAG, "${message ?: ""} $t")
            catchBlock(t)
        }
    }
}

fun <T> firebaseTry(message: String? = null, block: () -> T): FirebaseTry<T> =
    FirebaseTry(message, block)

fun <T> firebaseJustTry(message: String? = null, block: () -> T): T? {
    return try {
        if (message != null) {
            Firebase.crashlytics.log(message)
            Log.e(TAG, "$message")
        }
        block()
    } catch (t: Throwable) {
        Firebase.crashlytics.recordException(t)
        Log.e(TAG, "${message ?: ""} $t")
        null
    }
}

fun firebaseLog(message: String) {
    Firebase.crashlytics.log(message)
    Log.i(TAG, message)
}

fun firebaseLog(tag: String, message: String) {
    Firebase.crashlytics.log("$tag $message")
    Log.i(tag, message)
}

fun firebaseRecordException(t: Throwable) {
    Log.i(TAG, "Caught exception", t)
    Firebase.crashlytics.recordException(t)
}

fun firebaseRecordException(message: String, t: Throwable) {
    firebaseLog(message)
    firebaseRecordException(t)
}