package fi.iki.ede.safe

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import fi.iki.ede.logger.firebaseJustTry
import fi.iki.ede.logger.firebaseTry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TAG = "FirebaseLoggingTest"

class FirebaseLoggingTest {

    @Before
    fun before() {
        mockkObject(Firebase)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @After
    fun after() {
        unmockkObject(Firebase)
        unmockkStatic(FirebaseCrashlytics::class)
    }

    @Suppress("SameReturnValue", "Yes, intentionally, this is a method that throws")
    private fun throwException(): Int {
        // just to fool linter static analysis
        val x = Math.random()
        var y = Math.random()
        y *= 22
        if (x < y) {
            throw Exception("too much")
        }
        return 10
    }


    @Test
    fun testReturnTypeBubblesFromCatchBlock() {
        val q = firebaseTry("test") {
            throwException()
        }.firebaseCatch {
            Log.d(TAG, "Must see this! $it")
            2
        }
        assert(q == 2) { "Didn't go thru catch block" }
    }

    @Test
    fun testThatPlainTryReturnsRightTypeEvenWhenThrowing() {
        var z: Int? = null
        val h = firebaseJustTry {
            z = 11
            throwException()
        }
        assert(z == 11) { "oh no" }
    }

    @Test
    fun testThatReturnTypeIsCorrectWhenTryDoesntThrow() {
        val t = firebaseTry {
            Log.d(TAG, "Must see this too")
            2.2f
        }.firebaseCatch {
            Log.d(TAG, this.toString())
            assert(false) { "This line must never execute" }
            1.1f
        }
        assert(t == 2.2f) { "Failure" }
    }
}