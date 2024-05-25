package fi.iki.ede.crypto.date

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

object DateUtils {

    fun newParse(date: String): ZonedDateTime {
        val pattern = defaultDateFormat()
        val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
        return try {
            ZonedDateTime.from(fmt.parse(date))
        } catch (ex: DateTimeParseException) {
            val deprecatedPattern = "MMM d, y h:mm:ss a zzzz"
            val fmt2 = DateTimeFormatter.ofPattern(deprecatedPattern, Locale.ENGLISH)
            ZonedDateTime.from(fmt2.parse(date))
        }
    }

    fun newFormat(date: Date): String {
        val pattern = defaultDateFormat()
        val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
        return fmt.format(
            ZonedDateTime.ofInstant(
                date.toInstant(),
                ZoneId.of("UTC")
            )
        )
    }

    fun newFormat(date: ZonedDateTime): String {
        val pattern = defaultDateFormat()
        val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
        return fmt.format(date)
    }

//    fun durationBetweenDateAndNow(date: Date): Duration {
//        return Duration.between(
//            LocalDateTime.ofInstant(
//                date.toInstant(), ZoneId.systemDefault()
//            ), LocalDateTime.now()
//        )
//    }

    fun durationBetweenDateAndNow(date: ZonedDateTime): Duration {
        return Duration.between(
            LocalDateTime.ofInstant(
                date.toInstant(), ZoneId.systemDefault()
            ), LocalDateTime.now()
        )
    }

    private fun defaultDateFormat(): String = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.MEDIUM,
        FormatStyle.FULL, IsoChronology.INSTANCE, Locale.ENGLISH
    )
}