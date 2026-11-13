package io.github.allisson.bitcoinalert.data.repository

import io.github.allisson.bitcoinalert.data.binance.KlineEvent
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

private fun candle(openTime: Long, close: String = "100.00") =
    Candle(openTime, BigDecimal("100.00"), BigDecimal("100.00"), BigDecimal("100.00"), BigDecimal(close), BigDecimal("1"))

@OptIn(ExperimentalCoroutinesApi::class)
class CandleRepositoryTest {

    private val events = MutableSharedFlow<KlineEvent>(extraBufferCapacity = 64)
    private val loads = mutableListOf<Pair<Timeframe, CompletableDeferred<List<Candle>>>>()
    private val cancelled = mutableListOf<Timeframe>()

    private fun TestScope.repository() = CandleRepository(
        events,
        { tf ->
            val result = CompletableDeferred<List<Candle>>().also { loads += tf to it }
            try {
                result.await()
            } catch (e: CancellationException) {
                cancelled += tf
                throw e
            }
        },
        backgroundScope,
    ).also { runCurrent() }

    private fun TestScope.emit(vararg event: KlineEvent) {
        event.forEach { events.tryEmit(it) }
        runCurrent()
    }

    private fun TestScope.complete(index: Int, candles: List<Candle>) {
        loads[index].second.complete(candles)
        runCurrent()
    }

    @Test
    fun `merge replaces the same openTime, appends newer and discards older`() {
        val candles = listOf(candle(1), candle(2))

        assertEquals(listOf(candle(1), candle(2, "200.00")), mergeCandle(candles, candle(2, "200.00")))
        assertEquals(listOf(candle(1), candle(2), candle(3)), mergeCandle(candles, candle(3)))
        assertEquals(candles, mergeCandle(candles, candle(1, "300.00")))
        assertEquals(listOf(candle(5)), mergeCandle(emptyList(), candle(5)))
    }

    @Test
    fun `InEffect loads the candles, then applies the buffered updates`() = runTest {
        val repository = repository()

        emit(KlineEvent.InEffect(Timeframe.H1))
        assertEquals(CandleState.Loading, repository.state.value)
        assertEquals(listOf(Timeframe.H1), loads.map { it.first })
        emit(
            KlineEvent.Update(Timeframe.H1, candle(1, "1.00")),
            KlineEvent.Update(Timeframe.H1, candle(3, "3.30")),
            KlineEvent.Update(Timeframe.H1, candle(4)),
        )
        complete(0, listOf(candle(2), candle(3)))

        assertEquals(
            CandleState.Loaded(Timeframe.H1, listOf(candle(2), candle(3, "3.30"), candle(4))),
            repository.state.value,
        )
    }

    @Test
    fun `live updates merge into the loaded candles, other timeframes are ignored`() = runTest {
        val repository = repository()
        emit(KlineEvent.InEffect(Timeframe.H1))
        complete(0, listOf(candle(1)))

        emit(
            KlineEvent.Update(Timeframe.H1, candle(1, "1.10")),
            KlineEvent.Update(Timeframe.M5, candle(9)),
            KlineEvent.Update(Timeframe.H1, candle(2)),
        )

        assertEquals(CandleState.Loaded(Timeframe.H1, listOf(candle(1, "1.10"), candle(2))), repository.state.value)
    }

    @Test
    fun `a new InEffect cancels the load in flight and reloads`() = runTest {
        val repository = repository()
        emit(KlineEvent.InEffect(Timeframe.H1))

        emit(KlineEvent.InEffect(Timeframe.M15))
        complete(0, listOf(candle(1)))

        assertEquals(listOf(Timeframe.H1), cancelled)
        assertEquals(CandleState.Loading, repository.state.value)
        complete(1, listOf(candle(7)))
        assertEquals(CandleState.Loaded(Timeframe.M15, listOf(candle(7))), repository.state.value)
    }

    @Test
    fun `Removed cancels the load in flight`() = runTest {
        repository()
        emit(KlineEvent.InEffect(Timeframe.H1))

        emit(KlineEvent.Removed(Timeframe.H1))

        assertEquals(listOf(Timeframe.H1), cancelled)
    }

    @Test
    fun `a failed load shows the error and retry reloads`() = runTest {
        val repository = repository()
        emit(KlineEvent.InEffect(Timeframe.H1))
        loads[0].second.completeExceptionally(IOException("offline"))
        runCurrent()
        assertEquals(CandleState.Error(Timeframe.H1), repository.state.value)

        repository.retry()
        runCurrent()
        assertEquals(CandleState.Loading, repository.state.value)
        loads[1].second.completeExceptionally(SerializationException("bad price"))
        runCurrent()
        assertEquals(CandleState.Error(Timeframe.H1), repository.state.value)

        repository.retry()
        runCurrent()
        complete(2, listOf(candle(1)))
        assertEquals(CandleState.Loaded(Timeframe.H1, listOf(candle(1))), repository.state.value)
    }

    @Test
    fun `retry does nothing unless the load failed`() = runTest {
        val repository = repository()
        emit(KlineEvent.InEffect(Timeframe.H1))
        complete(0, listOf(candle(1)))

        repository.retry()
        runCurrent()

        assertEquals(1, loads.size)
    }
}
