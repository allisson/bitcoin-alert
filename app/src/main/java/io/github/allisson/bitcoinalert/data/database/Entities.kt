package io.github.allisson.bitcoinalert.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

// Prices are scale-2 decimal strings (toPlainString), never sorted in SQL (spec.md §3.5).

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val targetPrice: String,
    val direction: String,
    val enabled: Boolean,
    val triggeredAt: Long?,
    val createdAt: Long,
    val activationCycle: Int,
)

// No foreign key to price_alerts: deleting an alert keeps its history (spec.md §2.7).
@Entity(tableName = "alert_history")
data class AlertHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: String,
    val targetPrice: String,
    val observedPrice: String,
    val direction: String,
    val triggeredAt: Long,
)
