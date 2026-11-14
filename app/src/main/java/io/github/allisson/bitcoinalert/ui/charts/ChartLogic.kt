package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.ui.alerts.percentFromPrice
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** An alert line outside the candles' price range is pinned to that edge (spec.md §2.3). */
enum class Pin { Top, Bottom }

/** `null` when [target] is inside `[minY, maxY]`, so its line is drawn at the target. */
fun pinOf(target: Double, minY: Double, maxY: Double): Pin? = when {
    target > maxY -> Pin.Top
    target < minY -> Pin.Bottom
    else -> null
}

/**
 * `▲ 102,000  +4.71%`: the direction arrow, the target without decimals, its distance from the
 * current [price] (left out before the first price) and `↑`/`↓` when pinned to an edge.
 */
fun alertLabel(direction: AlertDirection, target: BigDecimal, price: BigDecimal?, pin: Pin?, locale: Locale): String = buildString {
    append(if (direction == AlertDirection.ABOVE) "▲ " else "▼ ")
    append(formatAxisPrice(target.toDouble(), locale))
    price?.let { append("  ").append(formatPercent(percentFromPrice(target, it), locale, signed = true)).append('%') }
    when (pin) {
        Pin.Top -> append(" ↑")
        Pin.Bottom -> append(" ↓")
        null -> Unit
    }
}

/** The alert a long press on the chart fills in (spec.md §2.3); Serializable to survive recreation. */
data class AlertDraft(val direction: AlertDirection, val target: BigDecimal) : Serializable

/**
 * The draft for a long press at canvas [y], on a price axis from [minY] at [bottom] to [maxY] at
 * [top]: the price there rounded to a whole dollar, ABOVE when it is at or over the current
 * [price], BELOW otherwise. `null` outside `[top, bottom]` (over the time axis).
 */
fun alertDraftAt(y: Float, top: Float, bottom: Float, minY: Double, maxY: Double, price: BigDecimal): AlertDraft? {
    if (y < top || y > bottom || bottom <= top) return null
    val value = maxY - (y - top) / (bottom - top) * (maxY - minY)
    val target = BigDecimal(value).setScale(0, RoundingMode.HALF_UP).setScale(2)
    if (target.signum() <= 0) return null
    return AlertDraft(if (target >= price) AlertDirection.ABOVE else AlertDirection.BELOW, target)
}

/** The candle's own change, from its open to its close, in percent with 2 decimal places. */
fun candleChange(candle: Candle): BigDecimal = percentFromPrice(candle.close, candle.open)
