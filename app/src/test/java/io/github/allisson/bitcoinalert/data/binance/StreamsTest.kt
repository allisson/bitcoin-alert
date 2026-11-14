package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val HOME_1H = setOf("btcusdt@ticker", "btcusdt@kline_1h")

@OptIn(ExperimentalCoroutinesApi::class)
class StreamsTest {
    @Test
    fun `stream set follows the subscription table`() {
        assertEquals(setOf("btcusdt@ticker", "btcusdt@kline_15m"), desiredStreams(true, false, false, Timeframe.M15))
        assertEquals(setOf("btcusdt@ticker", "btcusdt@kline_1d"), desiredStreams(true, false, true, Timeframe.D1))
        assertEquals(setOf("btcusdt@ticker"), desiredStreams(false, true, false, Timeframe.H1))
        assertEquals(setOf("btcusdt@ticker"), desiredStreams(false, false, true, Timeframe.H1))
        assertEquals(setOf("btcusdt@ticker"), desiredStreams(false, true, true, Timeframe.H1))
        assertEquals(emptySet<String>(), desiredStreams(false, false, false, Timeframe.H1))
    }

    private val visible = MutableStateFlow(false)
    private val alertsVisible = MutableStateFlow(false)
    private val timeframe = MutableStateFlow(Timeframe.H1)

    private fun kotlinx.coroutines.test.TestScope.collect(): List<Set<String>> {
        val sets = mutableListOf<Set<String>>()
        backgroundScope.launch { desiredStreamsFlow(visible, alertsVisible, timeframe).collect { sets += it } }
        runCurrent()
        return sets
    }

    @Test
    fun `showing Home adds the selected kline at once and a timeframe change replaces it`() = runTest {
        val sets = collect()
        visible.value = true
        runCurrent()
        timeframe.value = Timeframe.M5
        runCurrent()
        // The initial empty set goes out at once; the client starts Idle, so it changes nothing.
        assertEquals(listOf(emptySet(), HOME_1H, setOf("btcusdt@ticker", "btcusdt@kline_5m")), sets)
    }

    @Test
    fun `hiding Home closes the stream set after a second`() = runTest {
        visible.value = true
        val sets = collect()
        visible.value = false
        advanceTimeBy(999)
        runCurrent()
        assertEquals(listOf(HOME_1H), sets)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(HOME_1H, emptySet()), sets)
    }

    @Test
    fun `hiding and showing Home within a second keeps the stream set`() = runTest {
        visible.value = true
        val sets = collect()
        visible.value = false
        advanceTimeBy(500)
        visible.value = true
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(HOME_1H), sets)
    }

    @Test
    fun `timeframe changes with Home hidden change nothing`() = runTest {
        val sets = collect()
        advanceTimeBy(1_000)
        timeframe.value = Timeframe.D1
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(emptySet<String>()), sets)
    }

    @Test
    fun `going from Home to Alerts keeps the ticker and drops the kline after a second`() = runTest {
        visible.value = true
        val sets = collect()
        visible.value = false
        alertsVisible.value = true
        runCurrent()
        assertEquals(listOf(HOME_1H), sets)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(HOME_1H, setOf("btcusdt@ticker")), sets)
    }

    @Test
    fun `hiding Alerts closes the stream set after a second`() = runTest {
        alertsVisible.value = true
        val sets = collect()
        alertsVisible.value = false
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(setOf("btcusdt@ticker"), emptySet<String>()), sets)
    }
}
