package io.github.allisson.bitcoinalert.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<PriceAlertEntity>>

    @Insert
    abstract suspend fun insert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET enabled = 0 WHERE id = :id")
    abstract suspend fun disable(id: String)

    /**
     * Enabling and reactivating: one statement, so the counter never depends on a value read
     * earlier. An Active alert is left alone, so a repeated tap does not restart its baseline.
     */
    @Query(
        "UPDATE price_alerts SET enabled = 1, triggeredAt = NULL, activationCycle = activationCycle + 1 " +
            "WHERE id = :id AND (enabled = 0 OR triggeredAt IS NOT NULL)",
    )
    abstract suspend fun activate(id: String)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** The duplicate-trigger guard: a repeated or replaced activation changes zero rows. */
    @Query(
        "UPDATE price_alerts SET triggeredAt = :now " +
            "WHERE id = :id AND activationCycle = :activationCycle AND enabled = 1 AND triggeredAt IS NULL",
    )
    protected abstract suspend fun markTriggered(id: String, activationCycle: Int, now: Long): Int

    @Insert
    protected abstract suspend fun insertHistory(entry: AlertHistoryEntity): Long

    /** Triggers [alert]'s activation and returns the history entry id, or `null` if it was not Active (spec.md §3.5). */
    @Transaction
    open suspend fun trigger(alert: PriceAlertEntity, observedPrice: String, now: Long): Long? {
        if (markTriggered(alert.id, alert.activationCycle, now) != 1) return null
        return insertHistory(
            AlertHistoryEntity(
                alertId = alert.id,
                targetPrice = alert.targetPrice,
                observedPrice = observedPrice,
                direction = alert.direction,
                triggeredAt = now,
            ),
        )
    }

    @Query("SELECT * FROM alert_history ORDER BY triggeredAt DESC, id DESC")
    abstract fun observeHistory(): Flow<List<AlertHistoryEntity>>
}
