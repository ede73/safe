package fi.iki.ede.safe.ui.utilities

import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.BuildConfig
import kotlin.time.Duration

private const val TAG = "MT"

class MeasureTime(private val title: String = "") : AutoCloseable {
    private var startTime = System.nanoTime()
    private var lastLapTime = startTime
    private var ended = false

    fun start() {
        if (!BuildConfig.DEBUG) return
        val now = System.nanoTime()
        startTime = now
        lastLapTime = now
        Logger.i(makeTag(), "========== START ==========")
    }

    fun lap(message: String, maxDuration: Duration? = null) {
        if (!BuildConfig.DEBUG) return
        val now = System.nanoTime()
        val sinceStart = now - startTime
        val sinceLastLap = now - lastLapTime

        val msg =
            "It took ${formatTime(sinceStart)} since start / ${formatTime(sinceLastLap)} to perform $message"

        if (maxDuration != null && maxDuration.inWholeNanoseconds < sinceLastLap) {
            // shit, we exceeded allotted time
            Logger.e(makeTag(), "EXCEEDED ${formatTime(maxDuration.inWholeNanoseconds)} $msg")
        } else {
            Logger.i(makeTag(), msg)
        }
        lastLapTime = now
    }

    fun end(message: String, maxDuration: Duration? = null) {
        lap(message, maxDuration)
        Logger.i(makeTag(), "========== STOP ==========")
        ended = true
    }

    override fun close() {
        if (!ended) {
            end("You forgot to call .end()")
        }
    }

    private fun makeTag() = "$TAG${if (title.isEmpty()) "" else "-${title}"}"
    private fun formatTime(time: Long): String {
        return when {
            time < 1_000 -> "$time ns"
            time < 1_000_000 -> "${time / 1_000.0} us"
            time < 1_000_000_000 -> "${time / 1_000_000.0} ms"
            else -> "${time / 1_000_000_000.0} s"
        }
    }
}
