package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import kotlinx.datetime.DatePeriod

@Composable
fun getPasswordAgePlurality(duration: DatePeriod): String {
    val z = buildList {
        if (duration.years > 0) {
            add(getPluralString("password_list_password_age_years", duration.years, duration.years))
        }
        if (duration.months > 0) {
            add(getPluralString("password_list_password_age_months", duration.months, duration.months))
        }
        // if we're at scale of years and months...just skip days, wont make a big difference
        if (!(duration.years > 0 && duration.months > 0) && duration.days > 1 || (duration.months == 0 && duration.years == 0)) {
            add(getPluralString("password_list_password_age_days", duration.days, duration.days))
        }
    }.joinToString(" ")
    return z
}
