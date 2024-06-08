package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.theme.LocalSafeTheme
import fi.iki.ede.safe.ui.theme.SafeTheme
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale

// TODO: One can select a future date, limit it
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatePicker(zonedDateTime: ZonedDateTime?, onValueChange: (ZonedDateTime?) -> Unit) {
    val safeTheme = LocalSafeTheme.current
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

    val selectedYear = remember {
        mutableStateOf(quickFormat(zonedDateTime, "%04d", "----") { it.year })
    }
    val selectedMonth = remember {
        mutableStateOf(quickFormat(zonedDateTime, "%02d", "--") { it.monthValue })
    }
    val selectedDay = remember {
        mutableStateOf(quickFormat(zonedDateTime, "%02d", "--") { it.dayOfMonth })
    }

    val days = remember {
        derivedStateOf {
            val daysInMonth = daysInSelectedYearAndMonth(selectedYear, selectedMonth)
            (1..daysInMonth).map { it.toString().padStart(2, '0') } + "--"
        }
    }

    hasValueChanged(selectedYear, zonedDateTime, selectedMonth, selectedDay, onValueChange)

    val yearPagerState = rememberPagerState(
        pageCount = { years.size },
        initialPage = years.indexOf(selectedYear.value)
    )
    val monthPagerState = rememberPagerState(
        pageCount = { months.size },
        initialPage = months.indexOf(selectedMonth.value)
    )

    val dayPagerState =
        rememberPagerState(
            pageCount = { days.value.size },
            initialPage = {
                val result = days.value.indexOf(selectedDay.value)
                if (result == -1) {
                    days.value.size
                } else {
                    result
                }
            }()
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
    Column {
        Text(text = stringResource(id = R.string.password_entry_changed_date))
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalTextStyle provides safeTheme.customFonts.datePicker) {
                NumberPicker(years, selectedYear, yearPagerState)
                Text(text = "-")
                NumberPicker(months, selectedMonth, monthPagerState)
                Text(text = "-")
                NumberPicker(days.value, selectedDay, dayPagerState)
            }
        }
    }
}

private fun hasValueChanged(
    selectedYear: MutableState<String>,
    zonedDateTime: ZonedDateTime?,
    selectedMonth: MutableState<String>,
    selectedDay: MutableState<String>,
    onValueChange: (ZonedDateTime?) -> Unit
) {
    if (selectedYear.value != (zonedDateTime?.year ?: "----") &&
        (selectedMonth.value != (zonedDateTime?.monthValue ?: "--")) &&
        (selectedDay.value != (zonedDateTime?.dayOfMonth ?: "--"))
    ) {
        val selectedZonedTime = try {
            if (selectedYear.value.contains("-") ||
                selectedMonth.value.contains("_") ||
                selectedDay.value.contains("-")
            ) {
                null
            } else {
                val zone = zonedDateTime?.zone ?: ZonedDateTime.now().zone
                ZonedDateTime.of(
                    selectedYear.value.toInt(),
                    selectedMonth.value.toInt(),
                    selectedDay.value.toInt(), 0, 0, 0, 0, zone
                )
            }
        } catch (ex: Exception) {
            zonedDateTime
        }
        if (selectedZonedTime != zonedDateTime &&
            (selectedZonedTime?.toLocalDate() != zonedDateTime?.toLocalDate())
        ) {
            onValueChange(selectedZonedTime)
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

private fun quickFormat(
    zonedDateTime: ZonedDateTime?,
    format: String,
    default: String,
    comp: (ZonedDateTime) -> Int
) =
    zonedDateTime?.let {
        String.format(Locale.getDefault(), format, comp(it))
    } ?: default

@Preview(showBackground = true)
@Composable
fun DatePickerPreview() {
    SafeTheme {
        DatePicker(
            ZonedDateTime.now()
        ) {
        }
    }
}