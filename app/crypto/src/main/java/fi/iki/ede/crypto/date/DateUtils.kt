package fi.iki.ede.crypto.date

import android.util.Log
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.ChronoField

object DateUtils {

    const val TAG = "DateUtils"
    fun unixEpochSecondsToLocalZonedDateTime(unixEpochSeconds: Long) =
        Instant.ofEpochSecond(unixEpochSeconds)
            .atZone(ZoneId.systemDefault())

    fun toUnixSeconds(input: ZonedDateTime) =
        input.toEpochSecond()

    @Deprecated("DO NOT USE! Only allowed use case is DBHelper and Restore - for backwards compatibility")
    fun newParse(flakyStringDate: String): ZonedDateTime {
        val date = flakyStringDate.map { if (it.isWhitespace()) ' ' else it }.joinToString("")
            .replace("AM", "").replace("PM", "").replace("  ", " ")
            .replace(Regex("^(.*?)(\\d{1,2}:\\d{2}:\\d{2}).*$"), "$1$2")

        dateFormatPatterns.forEach { formatter ->
            try {
                return LocalDate.from(formatter.parse(date)).atStartOfDay(ZoneId.systemDefault())
            } catch (ex: DateTimeParseException) {
                Log.d(TAG, "..Failed $date with ${formatter}", ex)
            }
        }
        // nothing worked!
        throw DateTimeParseException(
            "Couldn't figure out date ($date) at all ",
            flakyStringDate,
            0
        )
    }

    fun durationBetweenDateAndNow(date: ZonedDateTime): Duration {
        return Duration.between(
            LocalDateTime.ofInstant(
                date.toInstant(), ZoneId.systemDefault()
            ), LocalDateTime.now()
        )
    }

    private val dateFormatPatterns = listOf(
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendPattern(" [d][,] [yyyy][,] [H]:mm:ss")
            .toFormatter()
    )
}