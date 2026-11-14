package io.github.allisson.bitcoinalert.ui.home

import io.github.allisson.bitcoinalert.domain.model.Ticker
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** A scale-2 price with the locale's grouping and exactly 2 decimal places. */
fun formatPrice(price: BigDecimal, locale: Locale): String = twoDecimals(locale).format(price)

/** The last price, or [placeholder] before the first ticker (spec.md §2.1). */
fun priceText(ticker: Ticker?, locale: Locale, placeholder: String): String =
    ticker?.let { formatPrice(it.lastPrice, locale) } ?: placeholder

/** A scale-2 percentage with the locale's decimal separator, no grouping; [signed] adds `+` to positive values. */
fun formatPercent(percent: BigDecimal, locale: Locale, signed: Boolean = false): String {
    val text = twoDecimals(locale).apply { isGroupingUsed = false }.format(percent)
    return if (signed && percent.signum() > 0) "+$text" else text
}

enum class ChangeDirection { Up, Down, Flat }

data class ChangeDisplay(val text: String, val direction: ChangeDirection)

/**
 * The 24-hour change with 2 decimal places (`HALF_UP`), sign and arrow; a value that rounds to
 * zero shows `0.00%` with neither (spec.md §2.1).
 */
fun formatChange(percent: BigDecimal, locale: Locale): ChangeDisplay {
    val rounded = percent.setScale(2, RoundingMode.HALF_UP)
    val abs = twoDecimals(locale).apply { isGroupingUsed = false }.format(rounded.abs())
    return when (rounded.signum()) {
        1 -> ChangeDisplay("▲ +$abs%", ChangeDirection.Up)
        -1 -> ChangeDisplay("▼ -$abs%", ChangeDirection.Down)
        else -> ChangeDisplay("$abs%", ChangeDirection.Flat)
    }
}

private fun twoDecimals(locale: Locale) = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
    roundingMode = RoundingMode.HALF_UP
}
