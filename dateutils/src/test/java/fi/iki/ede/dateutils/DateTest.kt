package fi.iki.ede.dateutils

import org.junit.Assert
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTest {

    private fun unixTimestampToZonedDateTime(unixTimestamp: Long, zoneId: ZoneId): ZonedDateTime {
        val startOfDay = Instant.ofEpochMilli(unixTimestamp)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
        return startOfDay
    }

    @Test
    fun assertNewProgramCanConvertNonZonedDates() {
        val unixDate = unixTimestampToZonedDateTime(UNIX_STAMP_MILLIS, ZoneId.of("UTC"))
        formats.forEach {
            Assert.assertEquals(
                unixDate.toLocalDate(),
                DateUtils.newParse(it).toLocalDate()
            )
        }
    }

    @Test
    fun failedOnceBeforeDateTest() {
        DateUtils.newParse("Jan 12, 2024, 12:44:37")
    }

    companion object {
        // Exactly Wed May 31 2023 22:21:47 GMT+0000
        private const val UNIX_STAMP_MILLIS = 1685571707000L

        // Not sure where the non breakable space popped from !?
        private const val NEW_DATE_FORMAT = "May 31, 2023, 10:21:47 PM Coordinated Universal Time"
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