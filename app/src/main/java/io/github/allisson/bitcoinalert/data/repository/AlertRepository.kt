package io.github.allisson.bitcoinalert.data.repository

import io.github.allisson.bitcoinalert.data.database.AlertDao
import io.github.allisson.bitcoinalert.data.database.AlertHistoryEntity
import io.github.allisson.bitcoinalert.data.database.PriceAlertEntity
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Alerts and their history in Room. [clock] gives epoch milliseconds. */
class AlertRepository(private val dao: AlertDao, private val clock: () -> Long) {
    /** Every alert, newest first (`createdAt DESC, id DESC`). */
    val alerts: Flow<List<PriceAlert>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Every trigger, newest first (`triggeredAt DESC, id DESC`). */
    val history: Flow<List<AlertHistoryEntry>> = dao.observeHistory().map { list -> list.map { it.toDomain() } }

    /** Creates an Active alert in cycle 0. [target] must already be validated (> 0, at most 2 decimals). */
    suspend fun create(direction: AlertDirection, target: BigDecimal): PriceAlert {
        val alert = PriceAlert(
            id = UUID.randomUUID().toString(),
            targetPrice = target.setScale(2, RoundingMode.UNNECESSARY),
            direction = direction,
            enabled = true,
            triggeredAt = null,
            createdAt = clock(),
            activationCycle = 0,
        )
        dao.insert(alert.toEntity())
        return alert
    }

    suspend fun disable(id: String) = dao.disable(id)

    /** Enables a Disabled alert or reactivates a Triggered one: a new activation cycle. */
    suspend fun activate(id: String) = dao.activate(id)

    /** Deletes the alert; its history stays. */
    suspend fun delete(id: String) = dao.delete(id)

    /** Triggers [alert]'s cycle; returns the history entry id, or `null` when that cycle is no longer Active. */
    suspend fun trigger(alert: PriceAlert, observedPrice: BigDecimal): Long? =
        dao.trigger(alert.toEntity(), observedPrice.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), clock())
}

private fun PriceAlertEntity.toDomain() = PriceAlert(
    id = id,
    targetPrice = BigDecimal(targetPrice),
    direction = AlertDirection.valueOf(direction),
    enabled = enabled,
    triggeredAt = triggeredAt,
    createdAt = createdAt,
    activationCycle = activationCycle,
)

private fun PriceAlert.toEntity() = PriceAlertEntity(
    id = id,
    targetPrice = targetPrice.toPlainString(),
    direction = direction.name,
    enabled = enabled,
    triggeredAt = triggeredAt,
    createdAt = createdAt,
    activationCycle = activationCycle,
)

private fun AlertHistoryEntity.toDomain() = AlertHistoryEntry(
    id = id,
    alertId = alertId,
    targetPrice = BigDecimal(targetPrice),
    observedPrice = BigDecimal(observedPrice),
    direction = AlertDirection.valueOf(direction),
    triggeredAt = triggeredAt,
)
