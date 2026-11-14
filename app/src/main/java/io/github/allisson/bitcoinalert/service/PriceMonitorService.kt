package io.github.allisson.bitcoinalert.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.domain.alerts.AlertEngine
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.notifications.ALERT_NOTIFICATION_ID
import io.github.allisson.bitcoinalert.notifications.MONITORING_NOTIFICATION_ID
import io.github.allisson.bitcoinalert.notifications.alertNotification
import io.github.allisson.bitcoinalert.notifications.monitoringNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PriceMonitorService"

/**
 * The monitoring Foreground Service (spec.md §2.8), in the main process: it shares the Binance
 * client, the DataStore and the running flag with the UI.
 */
class PriceMonitorService : Service() {
    private val app get() = application as BitcoinAlertApp
    private val scope = MainScope()
    private var activeAlerts: Int? = null

    /** The monitoring loop; a repeated start does not open a second one. */
    internal var monitoring: Job? = null
        private set

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app.monitoringController.serviceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First, before any suspending work: Android requires it within seconds of
        // startForegroundService, and a restarted service is no longer in the foreground.
        try {
            ServiceCompat.startForeground(
                this,
                MONITORING_NOTIFICATION_ID,
                monitoringNotification(this, activeAlerts),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException on a refused restart: the state becomes Interrupted.
            Log.w(TAG, "Foreground start refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            when {
                intent?.action == ACTION_STOP -> app.monitoringController.stop()
                // A restart after the process died between the user's stop and the service stop.
                intent == null && !app.settingsRepository.monitoringRequested.first() -> stopSelf()
                monitoring == null -> monitoring = launch {
                    try {
                        monitor()
                    } catch (e: SQLiteException) {
                        // Usually persistent (disk full, corruption): stop, so the state shows Interrupted, rather than crash and restart.
                        Log.w(TAG, "Database failure, stopping monitoring", e)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    // Without POST_NOTIFICATIONS Android drops the notifications; monitoring and the history go on (spec.md §2.6).
    @SuppressLint("MissingPermission")
    private suspend fun monitor() = coroutineScope {
        val notifications = NotificationManagerCompat.from(this@PriceMonitorService)
        val alerts = app.alertRepository.alerts.stateIn(this)
        launch {
            alerts.map { list -> list.count { it.state == AlertState.Active } }
                .distinctUntilChanged()
                .collect { count ->
                    activeAlerts = count
                    notifications.notify(MONITORING_NOTIFICATION_ID, monitoringNotification(this@PriceMonitorService, count))
                }
        }
        // A new engine per service instance: every start or restart takes a new baseline (spec.md §2.5).
        val engine = AlertEngine()
        // drop(1): the current ticker may predate the start (left from Home); the next one is the baseline.
        app.binanceStreamClient.ticker.drop(1).filterNotNull().collect { ticker ->
            for (alert in engine.onPrice(ticker.lastPrice, alerts.value)) {
                // Notify only after the trigger transaction commits; null when the cycle is no longer Active.
                val entry = app.alertRepository.trigger(alert, ticker.lastPrice) ?: continue
                notifications.notify(entry.id.toString(), ALERT_NOTIFICATION_ID, alertNotification(this@PriceMonitorService, entry))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        app.monitoringController.serviceRunning.value = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.allisson.bitcoinalert.action.STOP_MONITORING"
    }
}
