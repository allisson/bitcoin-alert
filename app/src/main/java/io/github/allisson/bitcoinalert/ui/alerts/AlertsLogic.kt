package io.github.allisson.bitcoinalert.ui.alerts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal
import java.math.RoundingMode

// Digits with at most one `.` or `,` and 1–2 digits after it, counted as typed (spec.md §2.5).
private val TARGET_INPUT = Regex("""\d+([.,]\d{1,2})?""")

/** The typed target at scale 2, or `null` when invalid. Never read with a locale-aware parser. */
fun parseTargetPrice(input: String): BigDecimal? {
    val text = input.trim()
    if (!TARGET_INPUT.matches(text)) return null
    val price = BigDecimal(text.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)
    return price.takeIf { it.signum() > 0 }
}

private val HUNDRED = BigDecimal(100)
private val FULL_BAR_DISTANCE = BigDecimal(10)

/** `(target − price) / price` in percent, 2 decimal places (`HALF_UP`); positive when the target is above. */
fun percentFromPrice(target: BigDecimal, price: BigDecimal): BigDecimal =
    (target - price).multiply(HUNDRED).divide(price, 2, RoundingMode.HALF_UP)

sealed interface Distance {
    /** The price is on the side the alert waits on; [fill] is 1 at the target and 0 at 10% or more away. */
    data class Away(val percent: BigDecimal, val fill: Float) : Distance

    /** The price is already past the target: the alert waits for it to come back and cross. */
    data object Beyond : Distance
}

fun distance(direction: AlertDirection, target: BigDecimal, price: BigDecimal): Distance {
    val beyond = when (direction) {
        AlertDirection.ABOVE -> price > target
        AlertDirection.BELOW -> price < target
    }
    if (beyond) return Distance.Beyond
    val percent = percentFromPrice(target, price).abs()
    val fill = BigDecimal.ONE - percent.divide(FULL_BAR_DISTANCE, 4, RoundingMode.HALF_UP)
    return Distance.Away(percent, fill.toFloat().coerceIn(0f, 1f))
}

/** A Triggered alert with the price observed when it triggered (`null` if that entry is missing). */
data class TriggeredAlert(val alert: PriceAlert, val observedPrice: BigDecimal?)

data class AlertSections(
    val active: List<PriceAlert>,
    val triggered: List<TriggeredAlert>,
    val disabled: List<PriceAlert>,
)

/**
 * Splits [alerts] (already `createdAt DESC, id DESC`) by state; Triggered goes by
 * `triggeredAt DESC, id DESC`, most recent first.
 */
fun alertSections(alerts: List<PriceAlert>, history: List<AlertHistoryEntry>): AlertSections {
    // shortcut: matches the history in Kotlin, reading all of it; a SQL join if history gets large.
    val observed = history.associateBy({ it.alertId to it.triggeredAt }, { it.observedPrice })
    val byState = alerts.groupBy { it.state }
    return AlertSections(
        active = byState[AlertState.Active].orEmpty(),
        triggered = byState[AlertState.Triggered].orEmpty()
            .sortedWith(compareByDescending<PriceAlert> { it.triggeredAt }.thenByDescending { it.id })
            .map { TriggeredAlert(it, observed[it.id to it.triggeredAt]) },
        disabled = byState[AlertState.Disabled].orEmpty(),
    )
}
