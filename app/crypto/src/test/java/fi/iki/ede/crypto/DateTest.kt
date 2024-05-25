package fi.iki.ede.crypto

import fi.iki.ede.crypto.date.DateUtils
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DateTest {
    @Test
    fun assertNewProgramCanConvertNonZonedDates() {
        val date = Date(UNIX_STAMP_MILLIS)
        val formattedDate = DateUtils.newFormat(date)
        Assert.assertEquals(NEW_DATE_FORMAT, formattedDate)

        val zonedDateTime = DateUtils.newParse(NEW_DATE_FORMAT)
        Assert.assertEquals(UNIX_STAMP_MILLIS, zonedDateTime.toEpochSecond() * 1000L)

        val zonedDateTime2 = DateUtils.newParse(NEW_DATE_FORMAT_PST)
        Assert.assertEquals(UNIX_STAMP_MILLIS, zonedDateTime2.toEpochSecond() * 1000L)
    }

    companion object {
        // Exactly Wed May 31 2023 22:21:47 GMT+0000
        private const val UNIX_STAMP_MILLIS = 1685571707000L
        private const val NEW_DATE_FORMAT = "May 31, 2023, 10:21:47 PM Coordinated Universal Time"
        private const val NEW_DATE_FORMAT_PST = "May 31, 2023, 03:21:47 PM Pacific Daylight Time"
    }
}