package io.github.allisson.bitcoinalert.domain.model

import java.math.BigDecimal

enum class AlertDirection { ABOVE, BELOW }

enum class AlertState { Active, Triggered, Disabled }

/** A price alert (spec.md §2.5). Prices are `BigDecimal` with scale 2. */
data class PriceAlert(
    val id: String,
    val targetPrice: BigDecimal,
    val direction: AlertDirection,
    val enabled: Boolean,
    val triggeredAt: Long?,
    val createdAt: Long,
    val activationCycle: Int,
) {
    val state: AlertState
        get() = when {
            !enabled -> AlertState.Disabled
            triggeredAt != null -> AlertState.Triggered
            else -> AlertState.Active
        }
}

/** One trigger, kept after its alert is deleted (spec.md §2.7). */
data class AlertHistoryEntry(
    val id: Long,
    val alertId: String,
    val targetPrice: BigDecimal,
    val observedPrice: BigDecimal,
    val direction: AlertDirection,
    val triggeredAt: Long,
)
