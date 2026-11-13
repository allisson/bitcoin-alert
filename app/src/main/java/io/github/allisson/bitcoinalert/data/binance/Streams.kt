package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe

const val TICKER_STREAM = "btcusdt@ticker"

val Timeframe.klineStream get() = "btcusdt@kline_$binanceInterval"

internal fun timeframeOfKlineStream(stream: String) = Timeframe.entries.firstOrNull { it.klineStream == stream }

/** The streams the connection must carry (spec.md §3.3 subscription table). */
fun desiredStreams(homeVisible: Boolean, monitoringRunning: Boolean, timeframe: Timeframe): Set<String> = when {
    homeVisible -> setOf(TICKER_STREAM, timeframe.klineStream)
    monitoringRunning -> setOf(TICKER_STREAM)
    else -> emptySet()
}

sealed interface KlineEvent {
    /** The kline stream is in effect: load the candles now (spec.md §3.3, "Loading candles"). */
    data class InEffect(val timeframe: Timeframe) : KlineEvent

    data class Update(val timeframe: Timeframe, val candle: Candle) : KlineEvent

    /** The kline stream left the desired set: cancel its candle load. */
    data class Removed(val timeframe: Timeframe) : KlineEvent
}
