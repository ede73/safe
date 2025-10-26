package fi.iki.ede.logger

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

private const val TAG = "FirebaseLoggingTest"

class FirebaseLoggingTest {

    @BeforeEach
    fun before() {
        mockkObject(Firebase)
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
    }

    @AfterEach
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
            Logger.d(TAG, "Must see this! $it")
            2
        }
        assertEquals(2, q, "Didn't go thru catch block")
    }

    @Test
    fun testThatPlainTryReturnsRightTypeEvenWhenThrowing() {
        var z: Int? = null
        firebaseJustTry {
            z = 11
            throwException()
        }
        assertEquals(11, z, "oh no")
    }

    @Test
    fun testThatReturnTypeIsCorrectWhenTryDoesntThrow() {
        val t = firebaseTry {
            Logger.d(TAG, "Must see this too")
            2.2f
        }.firebaseCatch {
            Logger.d(TAG, this.toString())
            fail("This line must never execute")
            1.1f
        }
        assertEquals(2.2f, t, "Failure")
    }
}
