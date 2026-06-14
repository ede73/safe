package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import kotlinx.datetime.DatePeriod

@Composable
fun getPasswordAgePlurality(duration: DatePeriod): String {
    val z = buildList {
        if (duration.years > 0) {
            val label = if (duration.years == 1) "year" else "years"
            add("${duration.years} $label")
        }
        if (duration.months > 0) {
            val label = if (duration.months == 1) "month" else "months"
            add("${duration.months} $label")
        }
        // if we're at scale of years and months...just skip days, wont make a big difference
        if (!(duration.years > 0 && duration.months > 0) && duration.days > 1 || (duration.months == 0 && duration.years == 0)) {
            val label = if (duration.days == 1) "day" else "days"
            add("${duration.days} $label")
        }
    }.joinToString(" ")
    return z
}
