package io.github.allisson.bitcoinalert.domain.alerts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal

/**
 * Detects price crossings (spec.md §2.5). Each activation cycle `(id, activationCycle)` keeps the
 * last price it saw: a cycle seen for the first time takes the price as its baseline and is
 * evaluated from the next one. One engine per monitoring session: a start or service restart
 * creates a new one, so every Active alert takes a new baseline, while a WebSocket reconnection
 * keeps it. Not thread-safe: call it from one coroutine.
 */
class AlertEngine {
    private val lastPrices = HashMap<Pair<String, Int>, BigDecimal>()

    /** Cycles with a baseline (for tests). */
    internal val trackedCycles: Set<Pair<String, Int>> get() = lastPrices.keys

    /**
     * Returns the alerts in [alerts] whose Active cycle [price] crossed. The caller triggers each
     * through the database transaction, which ignores a cycle that was replaced meanwhile.
     */
    fun onPrice(price: BigDecimal, alerts: List<PriceAlert>): List<PriceAlert> {
        val active = alerts.filter { it.state == AlertState.Active }
        // Drop the baselines of cycles that are no longer Active (triggered, disabled, deleted, replaced).
        lastPrices.keys.retainAll(active.mapTo(HashSet()) { it.id to it.activationCycle })
        return active.filter { alert ->
            val previous = lastPrices.put(alert.id to alert.activationCycle, price)
            previous != null && crossed(alert, previous, price)
        }
    }
}

/** Kotlin's `<`, `>` on `BigDecimal` use `compareTo`, so `100000.5` and `100000.50` are equal. */
internal fun crossed(alert: PriceAlert, previous: BigDecimal, current: BigDecimal): Boolean {
    val target = alert.targetPrice
    return when (alert.direction) {
        AlertDirection.ABOVE -> previous <= target && current > target
        AlertDirection.BELOW -> previous >= target && current < target
    }
}
