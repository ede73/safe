package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import fi.iki.ede.safe.R
import java.time.Period

@Composable
fun GetPasswordAgePlurality(duration: Period): String {
    val z = buildList {
        if (duration.years > 0) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_years,
                    count = duration.years,
                    duration.years
                )
            )
        }
        if (duration.months > 0) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_months,
                    count = duration.months,
                    duration.months
                )
            )
        }
        // if we're at scale of years and months...just skip days, wont make a big difference
        if (!(duration.years > 0 && duration.months > 0) && duration.days > 1 || (duration.months == 0 && duration.years == 0)) {
            add(
                pluralStringResource(
                    id = R.plurals.password_list_password_age_days,
                    count = duration.days,
                    duration.days
                )
            )
        }
    }.joinToString(" ")
    return z
}
