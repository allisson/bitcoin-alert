package io.github.allisson.bitcoinalert.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates and times in the device time zone and locale (spec.md §2.7), following the 12/24-hour setting. */
class DateFormats(private val locale: Locale, is24Hour: Boolean, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dayMonth = formatter("MMMd")
    private val dayMonthYear = formatter("yMMMd")
    private val time = formatter(if (is24Hour) "Hm" else "hm")

    private fun formatter(skeleton: String) =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private fun zoned(epochMillis: Long) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)

    /** `Nov 13`, with the year only when it is not the current one. */
    fun date(epochMillis: Long): String {
        val at = zoned(epochMillis)
        val sameYear = at.year == ZonedDateTime.now(zone).year
        return (if (sameYear) dayMonth else dayMonthYear).format(at)
    }

    fun time(epochMillis: Long): String = time.format(zoned(epochMillis))

    fun dateTime(epochMillis: Long): String = "${date(epochMillis)}, ${time(epochMillis)}"
}

@Composable
fun rememberDateFormats(): DateFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { DateFormats(locale, is24Hour) }
}
