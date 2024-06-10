package fi.iki.ede.safe.ui.models

import kotlin.time.TimeSource

class Progress(
    private val maxSize: Long,
    private val reportThresholdMillis: Long = 1000,
    private val report: (percentCompleted: Float) -> Unit
) {
    private var counter: Long = 0
    private var lastReport = TimeSource.Monotonic.markNow()

    fun increment() {
        counter++
        if (lastReport.elapsedNow().inWholeMilliseconds > reportThresholdMillis) {
            lastReport = TimeSource.Monotonic.markNow()
            report(100 * counter.toFloat() / maxSize.toFloat())
        }
    }
}