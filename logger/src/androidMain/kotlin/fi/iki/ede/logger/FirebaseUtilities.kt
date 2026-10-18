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

private inline fun withAnalytics(block: (com.google.firebase.analytics.FirebaseAnalytics) -> Unit) {
    val context = appContext
    if (context != null && FirebaseApp.getApps(context).isNotEmpty()) {
        try {
            block(com.google.firebase.analytics.FirebaseAnalytics.getInstance(context))
        } catch (e: Exception) {
            Logger.e(TAG, "Analytics error: ${e.message}")
        }
    }
}

actual fun firebaseLogEvent(eventName: String, params: Map<String, String>) {
    withAnalytics { analytics ->
        val bundle = android.os.Bundle()
        params.forEach { (k, v) -> bundle.putString(k, v) }
        analytics.logEvent(eventName, bundle)
    }
    firebaseLog("EVENT", "$eventName: $params")
}

actual fun firebaseSetCustomKey(key: String, value: String) {
    withCrashlytics { it.setCustomKey(key, value) }
}

class SyncFlowBreadcrumb(flowName: String, details: String) : Exception("[$flowName] $details")

actual fun firebasePublishFlowEvent(flowName: String, step: String, details: Map<String, String>) {
    withCrashlytics { crashlytics ->
        crashlytics.setCustomKey("flow_${flowName}", step)
        details.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
        crashlytics.log("[$flowName] $step: $details")
        crashlytics.recordException(SyncFlowBreadcrumb(flowName, "$step: $details"))
    }
    withAnalytics { analytics ->
        val bundle = android.os.Bundle()
        bundle.putString("step", step)
        details.forEach { (k, v) -> bundle.putString(k, v) }
        val safeEventName = "${flowName}_${step}".lowercase().filter { it.isLetterOrDigit() || it == '_' }.take(40)
        analytics.logEvent(safeEventName, bundle)
    }
    Logger.i("FirebaseFlow", "[$flowName] $step: $details")
}
