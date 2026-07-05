package fi.iki.ede.logger

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics

internal const val TAG = "fireebasehandler"

private var appContext: Context? = null

private inline fun withCrashlytics(block: (com.google.firebase.crashlytics.FirebaseCrashlytics) -> Unit) {
    val context = appContext
    if (context != null && FirebaseApp.getApps(context).isNotEmpty()) {
        block(Firebase.crashlytics)
    }
}

actual class FirebaseTry<T> actual constructor(
    private val message: String?,
    private val block: () -> T
) {
    actual fun firebaseCatch(catchBlock: (Throwable) -> T): T {
        return try {
            if (message != null) {
                withCrashlytics { it.log(message) }
                Logger.e(TAG, message)
            }
            block()
        } catch (t: Throwable) {
            withCrashlytics { it.recordException(t) }
            Logger.e(TAG, "${message ?: ""} $t")
            catchBlock(t)
        }
    }
}

// used for test crashing the app from preferences, do not assume any other use case
actual fun firebaseCollectCrashlytics(enabled: Boolean) {
    withCrashlytics { it.isCrashlyticsCollectionEnabled = enabled }
}

// This is the platform-specific part that needs a context
fun firebaseInitialize(
    context: Context, commitHash: String, versionName: String, versionCode: Int
) {
    appContext = context.applicationContext
    val isRunningTest = try {
        Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
    if (isRunningTest) {
        Logger.i(TAG, "Running under instrumentation test runner. Skipping Firebase initialization.")
        return
    }
    FirebaseApp.initializeApp(context)
    firebaseInitialize(commitHash, versionName, versionCode)
}

actual fun firebaseInitialize(
    commitHash: String, versionName: String, versionCode: Int
) {
    withCrashlytics {
        it.setCustomKey("git_commit_hash", commitHash)
        it.setCustomKey("VERSION_NAME", versionName)
        it.setCustomKey("VERSION_CODE", versionCode)
        it.isCrashlyticsCollectionEnabled = true
    }
}

actual fun <T> firebaseTry(message: String?, block: () -> T): FirebaseTry<T> =
    FirebaseTry(message, block)

actual fun <T> firebaseJustTry(message: String?, block: () -> T): T? {
    return try {
        if (message != null) {
            withCrashlytics { it.log(message) }
            Logger.e(TAG, message)
        }
        block()
    } catch (t: Throwable) {
        withCrashlytics { it.recordException(t) }
        Logger.e(TAG, "${message ?: ""} $t")
        null
    }
}

actual fun firebaseLog(message: String) {
    withCrashlytics { it.log(message) }
    Logger.i(TAG, message)
}

actual fun firebaseLog(tag: String, message: String) {
    withCrashlytics { it.log("$tag $message") }
    Logger.i(tag, message)
}

actual fun firebaseRecordException(t: Throwable) {
    Logger.i(TAG, "Caught exception", t)
    withCrashlytics { it.recordException(t) }
}

actual fun firebaseRecordException(message: String, t: Throwable) {
    firebaseLog(message)
    firebaseRecordException(t)
}
