package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.ui.alerts.percentFromPrice
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import java.math.BigDecimal
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

/** The candle's own change, from its open to its close, in percent with 2 decimal places. */
fun candleChange(candle: Candle): BigDecimal = percentFromPrice(candle.close, candle.open)
