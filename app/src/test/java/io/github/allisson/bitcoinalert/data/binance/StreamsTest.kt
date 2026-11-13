package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamsTest {
    @Test
    fun `stream set follows the subscription table`() {
        assertEquals(setOf("btcusdt@ticker", "btcusdt@kline_15m"), desiredStreams(true, false, Timeframe.M15))
        assertEquals(setOf("btcusdt@ticker", "btcusdt@kline_1d"), desiredStreams(true, true, Timeframe.D1))
        assertEquals(setOf("btcusdt@ticker"), desiredStreams(false, true, Timeframe.H1))
        assertEquals(emptySet<String>(), desiredStreams(false, false, Timeframe.H1))
    }
}
