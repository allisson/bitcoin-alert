package io.github.allisson.bitcoinalert.ui.home

import io.github.allisson.bitcoinalert.domain.model.ConnectionState
import io.github.allisson.bitcoinalert.domain.model.Ticker
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val ticker = MutableStateFlow<Ticker?>(null)

    private fun TestScope.ticker(receivedAt: Long = testScheduler.currentTime) =
        Ticker(BigDecimal("100000.00"), BigDecimal("1.000"), receivedAt)

    private fun TestScope.collectStale(): List<Boolean> {
        val values = mutableListOf<Boolean>()
        backgroundScope.launch { ticker.stale { testScheduler.currentTime }.collect { values += it } }
        runCurrent()
        return values
    }

    @Test
    fun `no ticker is never stale`() = runTest {
        val values = collectStale()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(false), values)
    }

    @Test
    fun `ticker becomes stale 10 seconds after it was received`() = runTest {
        val values = collectStale()
        ticker.value = ticker()
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(listOf(false), values.distinct())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(true, values.last())
    }

    @Test
    fun `a new ticker restarts the count`() = runTest {
        val values = collectStale()
        ticker.value = ticker()
        advanceTimeBy(8_000)
        ticker.value = ticker()
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(false, values.last())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(true, values.last())
    }

    @Test
    fun `stale data recovers when a ticker arrives`() = runTest {
        val values = collectStale()
        ticker.value = ticker()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(true, values.last())
        ticker.value = ticker()
        runCurrent()
        assertEquals(false, values.last())
    }

    @Test
    fun `a ticker already older than 10 seconds is stale at once`() = runTest {
        advanceTimeBy(20_000)
        val values = collectStale()
        ticker.value = ticker(receivedAt = testScheduler.currentTime - 11_000)
        runCurrent()
        assertEquals(true, values.last())
    }

    @Test
    fun `Idle is shown as Connecting, other states as they are`() {
        assertEquals(ConnectionState.Connecting, displayedConnection(ConnectionState.Idle))
        assertEquals(ConnectionState.Connected, displayedConnection(ConnectionState.Connected))
        assertEquals(ConnectionState.Reconnecting, displayedConnection(ConnectionState.Reconnecting))
        assertEquals(ConnectionState.Connecting, displayedConnection(ConnectionState.Connecting))
    }
}
