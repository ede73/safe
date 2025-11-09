package fi.iki.ede.datepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.dateutils.toLocalDate
import java.time.Year
import java.time.YearMonth
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// TODO: One can select a future date, limit it
@Composable
@ExperimentalTime
fun DatePicker(
    utcInstant: Instant?,
    datePickerFont: TextStyle = MaterialTheme.typography.titleLarge,
    onValueChange: (Instant?) -> Unit,
) {
    // Uh, there's a lot of chained events and other selective logic going on here
    // Basically simple, just select year,month,day
    // But we carry few states:
    // a) no initial input date -- no target date
    // b) initial input date -- no target date
    // c) no initial input date -- target date
    // d) initial input date -- target date
    // And some sub-states:
    // - for instance amount of days depends on year and month (derived)
    // - NumberPicker (pager) state one of year,month,day (or -- placeholders)
    // - Turn NumberPicker red if no date selected

    val currentYear = Year.now().value
    val years = (currentYear - 10..currentYear).map { it.toString() } + listOf("----")
    val months =
        (1..12).map { String.format(Locale.getDefault(), "%02d", it) } + listOf("--")

    val selectedYear = remember(utcInstant) {
        mutableStateOf(quickFormat(utcInstant, "%04d", "----") { it.toLocalDate().year })
    }
    val selectedMonth = remember(utcInstant) {
        mutableStateOf(quickFormat(utcInstant, "%02d", "--") { it.toLocalDate().monthNumber })
    }
    val selectedDay = remember(utcInstant) {
        mutableStateOf(quickFormat(utcInstant, "%02d", "--") { it.toLocalDate().dayOfMonth })
    }
    val days = remember(selectedYear, selectedDay) {
        derivedStateOf {
            val daysInMonth = daysInSelectedYearAndMonth(selectedYear, selectedMonth)
            (1..daysInMonth).map { it.toString().padStart(2, '0') } + "--"
        }
    }
    hasValueChanged(selectedYear, utcInstant, selectedMonth, selectedDay, onValueChange)

    val yearPagerState = rememberPagerState(
        pageCount = { years.size },
        initialPage = years.indexOf(selectedYear.value)
    )
    val monthPagerState = rememberPagerState(
        pageCount = { months.size },
        initialPage = months.indexOf(selectedMonth.value)
    )

    val result = days.value.indexOf(selectedDay.value)
    val dayPagerState =
        rememberPagerState(
            pageCount = { days.value.size },
            initialPage = if (result == -1) {
                days.value.size
            } else {
                result
            }
        )

    // Tiny issue here on null dates
    // When month (and year) is selected, days populate
    // But our picker was sitting at 0 (--), not updating initialPage
    // doesn't change the selector anymore, we need to scroll into the position
    if (!selectedYear.value.contains("-") &&
        !selectedMonth.value.contains("-") &&
        selectedDay.value == "--"
    ) {
        LaunchedEffect(Unit) {
            dayPagerState.scrollToPage(days.value.size)
        }
    }
    // Ensure yearPagerState and monthPagerState update correctly when zonedDateTime changes
    LaunchedEffect(utcInstant) {
        yearPagerState.scrollToPage(years.indexOf(selectedYear.value))
        monthPagerState.scrollToPage(months.indexOf(selectedMonth.value))
        dayPagerState.scrollToPage(days.value.indexOf(selectedDay.value))
    }
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompositionLocalProvider(LocalTextStyle provides datePickerFont) {
            NumberPicker(years, selectedYear, yearPagerState)
            Text(text = "-")
            NumberPicker(months, selectedMonth, monthPagerState)
            Text(text = "-")
            NumberPicker(days.value, selectedDay, dayPagerState)
        }
    }
}

@ExperimentalTime
private fun hasValueChanged(
    selectedYear: MutableState<String>,
    utcInstant: Instant?,
    selectedMonth: MutableState<String>,
    selectedDay: MutableState<String>,
    onValueChange: (Instant?) -> Unit
) {
    val localDate = utcInstant?.toLocalDate()
    if (selectedYear.value != (localDate?.year ?: "----") &&
        (selectedMonth.value != (localDate?.monthNumber ?: "--")) &&
        (selectedDay.value != (localDate?.dayOfMonth ?: "--"))
    ) {
        val selectedInstant = try {
            if (selectedYear.value.contains("-") ||
                selectedMonth.value.contains("_") ||
                selectedDay.value.contains("-")
            ) {
                null
            } else {
                utcInstant
            }
        } catch (ex: Exception) {
            utcInstant
        }
        if (selectedInstant != utcInstant) {
            onValueChange(selectedInstant)
        }
    }
}

private fun daysInSelectedYearAndMonth(
    selectedYear: MutableState<String>,
    selectedMonth: MutableState<String>
) = try {
    YearMonth.of(selectedYear.value.toInt(), selectedMonth.value.toInt())
        .lengthOfMonth()
} catch (ex: Exception) {
    0
}

@ExperimentalTime
private fun quickFormat(
    utcInstant: Instant?,
    format: String,
    default: String,
    comp: (Instant) -> Int
) = utcInstant?.let {
    String.format(Locale.getDefault(), format, comp(it))
} ?: default

@Preview(showBackground = true)
@Composable
@ExperimentalTime
fun DatePickerPreview() {
    MaterialTheme {
        DatePicker(Clock.System.now()) {}
    }
}