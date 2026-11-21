package io.github.allisson.bitcoinalert

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.allisson.bitcoinalert.data.binance.BinanceRestClient
import io.github.allisson.bitcoinalert.data.binance.BinanceStreamClient
import io.github.allisson.bitcoinalert.data.binance.OkHttpWebSocketConnector
import io.github.allisson.bitcoinalert.data.binance.WebSocketConnector
import io.github.allisson.bitcoinalert.data.binance.desiredStreamsFlow
import io.github.allisson.bitcoinalert.data.database.AppDatabase
import io.github.allisson.bitcoinalert.data.repository.AlertRepository
import io.github.allisson.bitcoinalert.data.repository.CandleRepository
import io.github.allisson.bitcoinalert.data.repository.SettingsRepository
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.notifications.ALERT_NOTIFICATION_ID
import io.github.allisson.bitcoinalert.notifications.alarmNotification
import io.github.allisson.bitcoinalert.notifications.createNotificationChannels
import io.github.allisson.bitcoinalert.service.AlarmController
import io.github.allisson.bitcoinalert.service.AlarmNotice
import io.github.allisson.bitcoinalert.service.AlarmOutput
import io.github.allisson.bitcoinalert.service.DeviceAlarmOutput
import io.github.allisson.bitcoinalert.service.MonitoringController
import io.github.allisson.bitcoinalert.service.PriceMonitorService
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Process-scoped instances (manual dependency injection). Open for a test subclass without network. */
open class BitcoinAlertApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Home's destination is at least `STARTED` (set by the Home screen). */
    val homeVisible = MutableStateFlow(false)

    /** Alerts' destination is at least `STARTED` (set by the Alerts screen). */
    val alertsVisible = MutableStateFlow(false)

    val httpClient by lazy { OkHttpClient() }

    val binanceRestClient by lazy { BinanceRestClient(httpClient) }

    val binanceStreamClient by lazy {
        BinanceStreamClient(webSocketConnector(), appScope, SystemClock::elapsedRealtime, Random.Default)
            .also { client ->
                getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = client.onNetworkAvailable()
                    },
                )
            }
    }

    val candleRepository by lazy {
        CandleRepository(binanceStreamClient.klineEvents, binanceRestClient::klines, appScope)
    }

    // The single DataStore instance: two on the same file throw (spec.md §3.5).
    val settingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { preferencesDataStoreFile("settings") })
    }

    protected open fun webSocketConnector(): WebSocketConnector = OkHttpWebSocketConnector(httpClient)

    val database by lazy { AppDatabase.build(this) }

    val alertRepository by lazy { AlertRepository(database.alertDao(), System::currentTimeMillis) }

    val monitoringController by lazy {
        val service = Intent(this, PriceMonitorService::class.java)
        MonitoringController(
            settingsRepository,
            startService = { ContextCompat.startForegroundService(this, service) },
            stopService = { stopService(service) },
        )
    }

    protected open fun alarmOutput(): AlarmOutput = DeviceAlarmOutput(this)

    // The service rings it; the alarm screen reads and dismisses it.
    val alarmController by lazy { AlarmController(alarmOutput(), MainScope(), ::postAlarm) }

    // Without POST_NOTIFICATIONS Android drops the notification; the alarm still rings (spec.md §2.6).
    @SuppressLint("MissingPermission")
    private fun postAlarm(entry: AlertHistoryEntry, notice: AlarmNotice) =
        NotificationManagerCompat.from(this).notify(entry.id.toString(), ALERT_NOTIFICATION_ID, alarmNotification(this, entry, notice))

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        candleRepository // subscribes to kline events before any stream is requested
        appScope.launch {
            desiredStreamsFlow(homeVisible, alertsVisible, monitoringController.serviceRunning, settingsRepository.timeframe)
                .collect(binanceStreamClient::setDesiredStreams)
        }
    }
}
