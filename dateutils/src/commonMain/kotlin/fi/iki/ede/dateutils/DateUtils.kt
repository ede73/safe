package fi.iki.ede.dateutils

import fi.iki.ede.logger.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

object DateUtils {

    const val TAG = "DateUtils"
    fun unixEpochSecondsToInstant(unixEpochSeconds: Long): Instant =
        Instant.fromEpochSeconds(unixEpochSeconds)

    fun toUnixSeconds(utcInput: Instant = Clock.System.now()) = utcInput.epochSeconds

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
        } catch (ex: Exception) {
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

    fun durationBetweenDateAndNow(date: Instant): Duration {
        return Clock.System.now() - date
    }
}

fun Instant.toLocalDate() = this.toLocalDateTime(TimeZone.currentSystemDefault()).date
fun Instant.toLocalTime() = this.toLocalDateTime(TimeZone.currentSystemDefault()).time
fun Instant.toLocalDateTime() = this.toLocalDateTime(TimeZone.currentSystemDefault())
