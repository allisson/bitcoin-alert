package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

const val TICKER_STREAM = "btcusdt@ticker"

val Timeframe.klineStream get() = "btcusdt@kline_$binanceInterval"

internal fun timeframeOfKlineStream(stream: String) = Timeframe.entries.firstOrNull { it.klineStream == stream }

/** The streams the connection must carry (spec.md §3.3 subscription table). */
fun desiredStreams(homeVisible: Boolean, monitoringRunning: Boolean, timeframe: Timeframe): Set<String> = when {
    homeVisible -> setOf(TICKER_STREAM, timeframe.klineStream)
    monitoringRunning -> setOf(TICKER_STREAM)
    else -> emptySet()
}

private const val HOME_HIDDEN_DELAY_MILLIS = 1_000L

/**
 * The desired stream set over time, with monitoring stopped (Phase 4 wires the real state).
 * Hiding Home takes effect after a second, so a configuration change (rotation, theme) that
 * stops and restarts the activity does not close and reopen the connection.
 */
@OptIn(FlowPreview::class)
fun desiredStreamsFlow(homeVisible: Flow<Boolean>, timeframe: Flow<Timeframe>): Flow<Set<String>> =
    combine(homeVisible.debounce { visible -> if (visible) 0 else HOME_HIDDEN_DELAY_MILLIS }, timeframe) { visible, tf ->
        desiredStreams(visible, monitoringRunning = false, tf)
    }.distinctUntilChanged()

sealed interface KlineEvent {
    /** The kline stream is in effect: load the candles now (spec.md §3.3, "Loading candles"). */
    data class InEffect(val timeframe: Timeframe) : KlineEvent

    data class Update(val timeframe: Timeframe, val candle: Candle) : KlineEvent

    /** The kline stream left the desired set: cancel its candle load. */
    data class Removed(val timeframe: Timeframe) : KlineEvent
}
