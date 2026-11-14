package io.github.allisson.bitcoinalert.ui.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.data.repository.AlertRepository
import io.github.allisson.bitcoinalert.data.repository.CandleRepository
import io.github.allisson.bitcoinalert.data.repository.CandleState
import io.github.allisson.bitcoinalert.data.repository.SettingsRepository
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.domain.model.ConnectionState
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import io.github.allisson.bitcoinalert.domain.model.Ticker
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.service.MonitoringController
import io.github.allisson.bitcoinalert.ui.launchUserWrite
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

private const val STALE_MILLIS = 10_000L

data class MonitoringUi(val state: MonitoringState, val activeAlerts: Int)

/** [timeframe] is `null` until the stored one is read. */
data class HomeUiState(
    val ticker: Ticker? = null,
    val stale: Boolean = false,
    val connection: ConnectionState = ConnectionState.Connecting,
    val timeframe: Timeframe? = null,
    val candles: CandleState = CandleState.Loading,
)

/**
 * Whether the last ticker is older than 10 s, re-evaluated when that age is reached; `false`
 * before the first ticker, which shows the placeholder instead (spec.md §2.1). [clock] is the
 * monotonic clock of [Ticker.receivedAt].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<Ticker?>.stale(clock: () -> Long): Flow<Boolean> = transformLatest { ticker ->
    if (ticker == null) {
        emit(false)
        return@transformLatest
    }
    val remaining = STALE_MILLIS - (clock() - ticker.receivedAt)
    if (remaining > 0) {
        emit(false)
        delay(remaining)
    }
    emit(true)
}

/** Home never shows a disconnected state: `Idle` lasts only until Home's stream set is applied. */
internal fun displayedConnection(state: ConnectionState) =
    if (state == ConnectionState.Idle) ConnectionState.Connecting else state

class HomeViewModel(
    ticker: StateFlow<Ticker?>,
    connection: StateFlow<ConnectionState>,
    private val candleRepository: CandleRepository,
    private val settings: SettingsRepository,
    private val monitoringController: MonitoringController,
    private val alertRepository: AlertRepository,
) : ViewModel() {
    private val alerts = alertRepository.alerts

    val uiState: StateFlow<HomeUiState> = combine(
        ticker,
        ticker.stale(SystemClock::elapsedRealtime),
        connection.map(::displayedConnection),
        settings.timeframe.map<Timeframe, Timeframe?> { it }.onStart { emit(null) },
        candleRepository.state,
    ) { t, stale, conn, tf, candles -> HomeUiState(t, stale, conn, tf, candles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Drawn as lines on the chart (spec.md §2.3). */
    val activeAlerts: StateFlow<List<PriceAlert>> = alerts.map { list -> list.filter { it.state == AlertState.Active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** `null` until the stored request flag and the alerts are read. */
    val monitoring: StateFlow<MonitoringUi?> = combine(
        monitoringController.state,
        alerts.map { list -> list.count { it.state == AlertState.Active } },
        ::MonitoringUi,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startMonitoring() {
        viewModelScope.launchUserWrite {
            try {
                monitoringController.start()
            } catch (e: IllegalStateException) {
                // Android refused the start; nothing was written, so the state stays as it was.
                Log.w("HomeViewModel", "Monitoring start refused", e)
            }
        }
    }

    fun stopMonitoring() {
        viewModelScope.launchUserWrite { monitoringController.stop() }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        viewModelScope.launchUserWrite { settings.setTimeframe(timeframe) }
    }

    fun retryCandles() = candleRepository.retry()

    /** From a long press on the chart. */
    fun createAlert(direction: AlertDirection, target: BigDecimal) {
        viewModelScope.launchUserWrite { alertRepository.create(direction, target) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BitcoinAlertApp
                HomeViewModel(
                    app.binanceStreamClient.ticker,
                    app.binanceStreamClient.connectionState,
                    app.candleRepository,
                    app.settingsRepository,
                    app.monitoringController,
                    app.alertRepository,
                )
            }
        }
    }
}
