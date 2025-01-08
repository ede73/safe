package fi.iki.ede.safe.ui.utilities

import android.util.Log
import fi.iki.ede.safe.BuildConfig

class MeasureTime(private val title: String = "") {
    private val TAG = "MeasureTime"
    private var startTime = System.nanoTime()
    private var lastLapTime = System.nanoTime()

    fun start() {
        if (!BuildConfig.DEBUG) return
        val now = System.nanoTime()
        startTime = now
        lastLapTime = now
    }

    fun lap(message: String) {
        if (!BuildConfig.DEBUG) return
        val now = System.nanoTime()
        val sinceStart = now - startTime
        val sinceLastLap = now - lastLapTime
        Log.e(
            "$TAG${if (title.isEmpty()) "" else " - $title"}",
            "It took ${formatTime(sinceStart)} since start / ${formatTime(sinceLastLap)} to perform $message"
        )
        lastLapTime = now
    }

    private fun formatTime(time: Long): String {
        return when {
            time < 1_000 -> "$time ns"
            time < 1_000_000 -> "${time / 1_000.0} us"
            time < 1_000_000_000 -> "${time / 1_000_000.0} ms"
            else -> "${time / 1_000_000_000.0} s"
        }
    }
}
