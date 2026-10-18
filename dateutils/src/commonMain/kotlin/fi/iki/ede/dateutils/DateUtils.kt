package fi.iki.ede.dateutils

import fi.iki.ede.logger.Logger
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.daysUntil
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExperimentalTime
object DateUtils {

    const val TAG = "DateUtils"
    fun daysBetween(startDateTime: Instant, endDateTime: Instant): Int {
        val startDate = startDateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val endDate = endDateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return startDate.daysUntil(endDate)
    }

    /**
     * Backward compatibility helper: The old app stored soft-delete timestamps in milliseconds
     * (System.currentTimeMillis()) in memory and exports, but expected seconds for age calculations.
     * This function normalizes millisecond timestamps (> 5 billion seconds, i.e. year 2128+) to seconds.
     */
    fun normalizeTimestampToSeconds(timestamp: Long): Long {
        val thresholdSeconds = 5_000_000_000L
        return if (timestamp > thresholdSeconds) timestamp / 1000 else timestamp
    }
    fun unixEpochSecondsToInstant(unixEpochSeconds: Long): Instant =
        Instant.fromEpochSeconds(unixEpochSeconds)

    fun toUnixSeconds(utcInput: Instant = Clock.System.now()) =
        utcInput.epochSeconds

    @Deprecated("DO NOT USE! Only allowed use case is DBHelper and Restore - for backwards compatibility")
    fun newParse(flakyStringDate: String): Instant {
        val date = flakyStringDate.map { if (it.isWhitespace()) ' ' else it }.joinToString("")
            .replace("AM", "").replace("PM", "").replace("  ", " ")
            .replace(Regex("^(.*?)(\\d{1,2}:\\d{2}:\\d{2}).*$"), "$1$2")

        // This is a placeholder for the parsing logic, which needs to be re-implemented in a KMP-compatible way.
        // For now, it will fail.
        // You will need a library or custom code for robust date string parsing in common code.
        try {
            return LocalDateTime.parse(date).toInstant(TimeZone.UTC)
        } catch (ex: IllegalArgumentException) {
            Logger.d(TAG, "..Failed $date", ex)
        }

        // Biometrics calls us too..
        flakyStringDate.toLongOrNull()?.let {
            return unixEpochSecondsToInstant(it)
        }
        // nothing worked!
        throw IllegalArgumentException("Couldn't figure out date ('$date' ie. '$flakyStringDate')) at all ")
    }

    fun getPeriodBetweenDates(
        startDateTime: Instant = Clock.System.now(),
        endDateTime: Instant = Clock.System.now()
    ): DatePeriod {
        val startDate = startDateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val endDate = endDateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return startDate.periodUntil(endDate)
    }

    fun formatTimeAgo(epochMillis: Long): String {
        if (epochMillis <= 0L) return "never"
        val now = Clock.System.now().toEpochMilliseconds()
        val diffMillis = now - epochMillis
        if (diffMillis < 45_000L) {
            return "just now"
        }
        val minutes = diffMillis / 60_000L
        if (minutes < 60) {
            return if (minutes <= 1L) "1 minute ago" else "$minutes minutes ago"
        }
        val hours = minutes / 60L
        if (hours < 24) {
            return if (hours <= 1L) "1 hour ago" else "$hours hours ago"
        }
        val days = hours / 24L
        if (days < 30) {
            return if (days <= 1L) "1 day ago" else "$days days ago"
        }
        if (days < 365) {
            val months = days / 30
            return if (months <= 1L) "1 month ago" else "$months months ago"
        }
        val years = days / 365
        val remainingMonths = (days % 365) / 30
        return if (years <= 1L) {
            if (remainingMonths > 0) "1 year $remainingMonths months ago" else "1 year ago"
        } else {
            if (remainingMonths > 0) "$years years $remainingMonths months ago" else "$years years ago"
        }
    }
}

@ExperimentalTime
fun Instant.toLocalDate() = this.toLocalDateTime(TimeZone.currentSystemDefault()).date

@ExperimentalTime
fun Instant.toLocalDateTime() = this.toLocalDateTime(TimeZone.currentSystemDefault())
