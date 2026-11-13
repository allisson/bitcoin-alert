package io.github.allisson.bitcoinalert.data.repository

import android.util.Log
import io.github.allisson.bitcoinalert.data.binance.KlineEvent
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

sealed interface CandleState {
    data object Loading : CandleState

    data class Loaded(val timeframe: Timeframe, val candles: List<Candle>) : CandleState

    data class Error(val timeframe: Timeframe) : CandleState
}

/** Same `openTime` as the last candle replaces it, newer is appended, older is discarded (spec.md §2.3). */
fun mergeCandle(candles: List<Candle>, update: Candle): List<Candle> {
    val last = candles.lastOrNull() ?: return listOf(update)
    return when {
        update.openTime == last.openTime -> candles.dropLast(1) + update
        update.openTime > last.openTime -> candles + update
        else -> candles
    }
}

/**
 * Candles of the selected timeframe: each [KlineEvent.InEffect] reloads them through REST while
 * buffering the stream's updates, then applies the buffer (spec.md §3.3, "Loading candles").
 */
class CandleRepository(
    klineEvents: Flow<KlineEvent>,
    private val loadCandles: suspend (Timeframe) -> List<Candle>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<CandleState>(CandleState.Loading)
    val state: StateFlow<CandleState> = _state.asStateFlow()

    private sealed interface Input {
        class Kline(val event: KlineEvent) : Input
        class LoadDone(val load: Int, val result: Result<List<Candle>>) : Input
        data object Retry : Input
    }

    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private var timeframe: Timeframe? = null
    private var load = 0
    private var loadJob: Job? = null
    private var buffer: MutableList<Candle>? = null // non-null while a load is in flight

    init {
        // Undispatched, so the collector is subscribed before the first event can be emitted.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { klineEvents.collect { inputs.send(Input.Kline(it)) } }
        scope.launch { for (input in inputs) handle(input) }
    }

    fun retry() {
        inputs.trySend(Input.Retry)
    }

    private fun handle(input: Input) {
        when (input) {
            is Input.Kline -> when (val event = input.event) {
                is KlineEvent.InEffect -> {
                    timeframe = event.timeframe
                    startLoad(event.timeframe)
                }
                is KlineEvent.Removed -> if (event.timeframe == timeframe) {
                    loadJob?.cancel()
                    buffer = null
                    timeframe = null
                }
                is KlineEvent.Update -> if (event.timeframe == timeframe) {
                    val pending = buffer
                    val loaded = _state.value
                    if (pending != null) {
                        pending += event.candle
                    } else if (loaded is CandleState.Loaded && loaded.timeframe == event.timeframe) {
                        _state.value = loaded.copy(candles = mergeCandle(loaded.candles, event.candle))
                    }
                }
            }
            is Input.LoadDone -> if (input.load == load && buffer != null) {
                val tf = timeframe ?: return
                input.result
                    .onSuccess { loaded ->
                        _state.value = CandleState.Loaded(tf, buffer!!.fold(loaded, ::mergeCandle))
                    }
                    .onFailure {
                        Log.w("Candles", "Candle load failed", it)
                        _state.value = CandleState.Error(tf)
                    }
                buffer = null
            }
            Input.Retry -> {
                val tf = timeframe
                if (tf != null && _state.value is CandleState.Error) startLoad(tf)
            }
        }
    }

    private fun startLoad(tf: Timeframe) {
        loadJob?.cancel()
        buffer = mutableListOf()
        _state.value = CandleState.Loading
        val id = ++load
        loadJob = scope.launch {
            val result = try {
                Result.success(loadCandles(tf))
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: SerializationException) {
                Result.failure(e)
            }
            inputs.send(Input.LoadDone(id, result))
        }
    }
}
