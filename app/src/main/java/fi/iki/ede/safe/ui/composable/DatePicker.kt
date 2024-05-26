package fi.iki.ede.safe.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.iki.ede.safe.R
import java.time.Year
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale

// TODO: with no date, select year,month, day pops to 01 (but not 'selected' by user so no event)
// on ui it looks like date is set, say 2024-12-01 but callback is not called..only after USER selects date
// FIX: in this case initialize date to "--" rather forcing the act of the user
//
// TODO: One can select a future date, limit it
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatePicker(zonedDateTime: ZonedDateTime?, onValueChange: (ZonedDateTime?) -> Unit) {
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
        rememberPagerState(pageCount = { days.value.size }, initialPage = {
            val result = days.value.indexOf(selectedDay.value)
            if (result == -1) {
                days.value.size
            } else {
                result
            }
        }())

    Column {
        Text(text = stringResource(id = R.string.password_entry_changed_date))
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NumberPicker(years, selectedYear, yearPagerState)
            Text(text = "-")
            NumberPicker(months, selectedMonth, monthPagerState)
            Text(text = "-")
            NumberPicker(days.value, selectedDay, dayPagerState)
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
        if (selectedZonedTime != zonedDateTime) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberPicker(
    numbers: List<String>,
    selectedNumber: MutableState<String>,
    pagerState: PagerState
) {
    LaunchedEffect(pagerState.currentPage) {
        selectedNumber.value = numbers[pagerState.currentPage]
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.height(35.dp)
    ) { page ->
        Text(
            text = numbers[page],
            color = if (numbers[page].contains("-")) Color.Red else Color.Unspecified,
            fontSize = 30.sp
        )
    }
}
