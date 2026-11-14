package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.withIndex

const val TICKER_STREAM = "btcusdt@ticker"

val Timeframe.klineStream get() = "btcusdt@kline_$binanceInterval"

internal fun timeframeOfKlineStream(stream: String) = Timeframe.entries.firstOrNull { it.klineStream == stream }

/** The streams the connection must carry (spec.md §3.3 subscription table). */
fun desiredStreams(homeVisible: Boolean, alertsVisible: Boolean, monitoringRunning: Boolean, timeframe: Timeframe): Set<String> =
    when {
        homeVisible -> setOf(TICKER_STREAM, timeframe.klineStream)
        // Alerts shows the distance from the current price to each target.
        alertsVisible || monitoringRunning -> setOf(TICKER_STREAM)
        else -> emptySet()
    }

private const val SCREEN_HIDDEN_DELAY_MILLIS = 1_000L

/**
 * Hiding a screen takes effect after a second, so a configuration change (rotation, theme) that
 * stops and restarts the activity does not close and reopen the connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<Boolean>.hiddenAfterDelay() = withIndex().transformLatest { (index, visible) ->
    // The initial value goes out at once, so a screen hidden at launch does not delay the others.
    if (!visible && index > 0) delay(SCREEN_HIDDEN_DELAY_MILLIS)
    emit(visible)
}

/** The desired stream set over time, with monitoring stopped (Phase 4 wires the real state). */
fun desiredStreamsFlow(homeVisible: Flow<Boolean>, alertsVisible: Flow<Boolean>, timeframe: Flow<Timeframe>): Flow<Set<String>> =
    combine(homeVisible.hiddenAfterDelay(), alertsVisible.hiddenAfterDelay(), timeframe) { home, alerts, tf ->
        desiredStreams(home, alerts, monitoringRunning = false, tf)
    }.distinctUntilChanged()

sealed interface KlineEvent {
    /** The kline stream is in effect: load the candles now (spec.md §3.3, "Loading candles"). */
    data class InEffect(val timeframe: Timeframe) : KlineEvent

    data class Update(val timeframe: Timeframe, val candle: Candle) : KlineEvent

    /** The kline stream left the desired set: cancel its candle load. */
    data class Removed(val timeframe: Timeframe) : KlineEvent
}
