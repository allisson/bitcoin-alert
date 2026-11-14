package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.ui.alerts.percentFromPrice
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** An alert line or the last price outside the visible candles' price range is pinned to that edge (spec.md §2.3). */
enum class Pin { Top, Bottom }

/** `null` when [target] is inside `[minY, maxY]`, so its line is drawn at the target. */
fun pinOf(target: Double, minY: Double, maxY: Double): Pin? = when {
    target > maxY -> Pin.Top
    target < minY -> Pin.Bottom
    else -> null
}

/**
 * The open times shown in a chart [width] wide, as a range that changes only when a candle enters
 * or leaves: candle `i` (x = [minX] + i × [xStep]) is centered [startPadding] + i × [xSpacing] −
 * [scroll] from the left edge, and counts while any of its slot is on screen.
 */
fun visibleX(scroll: Float, width: Float, startPadding: Float, xSpacing: Float, minX: Double, xStep: Double): ClosedFloatingPointRange<Double> {
    val first = floor((scroll - startPadding - xSpacing / 2) / xSpacing) + 1
    val last = ceil((scroll + width - startPadding + xSpacing / 2) / xSpacing) - 1
    return (minX + first * xStep)..(minX + last * xStep)
}

/** The price axis and the highest volume bar for the candles shown (spec.md §2.3). */
data class VisibleFit(val minY: Double, val maxY: Double, val maxVolume: Double)

/** Fit of the candles with an open time in [x], or of every candle before the first draw (`null`). */
fun visibleFit(candles: List<Candle>, x: ClosedFloatingPointRange<Double>?): VisibleFit? {
    val shown = if (x == null) candles else candles.filter { it.openTime.toDouble() in x }
    if (shown.isEmpty()) return null
    return VisibleFit(shown.minOf { it.low }.toDouble(), shown.maxOf { it.high }.toDouble(), shown.maxOf { it.volume }.toDouble())
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

/**
 * Label centers for labels [height] tall wanted at the [desired] centers (same order back), kept
 * [gap] apart inside `[top, bottom]` (spec.md §2.3): in order from the top, a label that would touch
 * the one above moves down, and a stack past an edge moves back inside. Labels that cannot all fit
 * are spread evenly over the area, overlapping.
 */
fun stackLabels(desired: List<Float>, height: Float, gap: Float, top: Float, bottom: Float): List<Float> {
    val first = top + height / 2
    val last = bottom - height / 2
    if (desired.isEmpty()) return emptyList()
    if (last <= first) return desired.map { (top + bottom) / 2 }
    val order = desired.indices.sortedBy { desired[it] }
    val n = desired.size
    val ys = FloatArray(n)
    val step = height + gap
    if (n > 1 && (n - 1) * step > last - first) {
        val even = (last - first) / (n - 1)
        order.forEachIndexed { rank, i -> ys[i] = first + rank * even }
        return ys.toList()
    }
    var previous = Float.NEGATIVE_INFINITY
    for (i in order) {
        ys[i] = maxOf(desired[i].coerceIn(first, last), previous + step)
        previous = ys[i]
    }
    var next = Float.POSITIVE_INFINITY
    for (i in order.reversed()) {
        ys[i] = minOf(ys[i], last, next - step)
        next = ys[i]
    }
    return ys.toList()
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
