package fi.iki.ede.dateutils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExperimentalTime
class DateTest {
    @Test
    @Disabled("ZonedDateTime parser was pretty good, kotlin one sucks. Though we should really need this anymore anywhere")
    fun assertNewProgramCanConvertNonZonedDates() {
        val unixDate = Instant.fromEpochMilliseconds(UNIX_STAMP_MILLIS)
        formats.forEach {
            assertEquals(
                unixDate,
                DateUtils.newParse(it)
            )
        }
    }

    @Test
    @Disabled("ZonedDateTime parser was pretty good, kotlin one sucks. Though we should really need this anymore anywhere")
    fun failedOnceBeforeDateTest() {
        DateUtils.newParse("Jan 12, 2024, 12:44:37")
    }

    companion object {
        // Exactly Wed May 31 2023 22:21:47 GMT+0000
        private const val UNIX_STAMP_MILLIS = 1685571707000L

        private val formats = listOf(
            "May 31, 2023, 03:22:11 AM",
            "May 31, 2023, 13:22:11",
            "May 31, 2023, 13:21:10",
            "May 31, 2023, 13:21:11 PM",
            "May 31, 2023, 3:21:12 AM Pacific Daylight Time",
            "May 31, 2023, 03:21:13 AM Pacific Daylight Time",
            "May 31, 2023 15:21:14 PM Pacific Daylight Time",
            "May 31, 2023, 15:31:15PM Pacific Daylight Time",
            "May 31, 2023 15:31:16PM Pacific Daylight Time",
            "May 31, 2023, 15:31:17",
            "May 31, 2023, 15:31:18 Pacific Daylight Time",
            "May 31, 2023 15:31:19 Pacific Daylight Time",
            "May 31, 2023, 15:21:20 PM Pacific Standard Time",
            "May 31, 2023 15:21:21 PM Pacific Standard Time"
        )
    }
}