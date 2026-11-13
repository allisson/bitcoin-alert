package io.github.allisson.bitcoinalert.data.binance

import android.util.Log
import io.github.allisson.bitcoinalert.domain.model.ConnectionState
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Connected
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Connecting
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Idle
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Reconnecting
import io.github.allisson.bitcoinalert.domain.model.Ticker
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "Binance"
private const val STREAM_URL = "wss://stream.binance.com:443/stream?streams="
private const val WATCHDOG_MILLIS = 10_000L
private const val STABLE_CONNECTION_MILLIS = 60_000L

/** Wait after the [failures]-th consecutive failure: `min(1 s × 2ⁿ⁻¹, 60 s) × (1 + U[0, 0.2])` (spec.md §2.8). */
internal fun backoffDelayMillis(failures: Int, random: Random): Long {
    val base = minOf(1_000L shl (failures - 1).coerceAtMost(6), 60_000L)
    return base + (base * 0.2 * random.nextDouble()).toLong()
}

/**
 * The process-wide Binance market stream client: one combined-stream WebSocket connection.
 * All state is owned by a single coroutine consuming [events]; callbacks and timers only post to it.
 *
 * @param clock monotonic milliseconds (elapsedRealtime in production).
 */
class BinanceStreamClient(
    private val connector: WebSocketConnector,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val random: Random,
) {
    private val _connectionState = MutableStateFlow(Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _ticker = MutableStateFlow<Ticker?>(null)
    val ticker: StateFlow<Ticker?> = _ticker.asStateFlow()

    private class Attempt {
        lateinit var connection: WebSocketConnector.Connection
    }

    private sealed interface Event {
        class DesiredStreams(val streams: Set<String>) : Event
        data object NetworkAvailable : Event
        data object RetryDue : Event
        class Opened(val attempt: Attempt) : Event
        class Message(val attempt: Attempt, val text: String) : Event
        class Closed(val attempt: Attempt) : Event
        class WatchdogExpired(val attempt: Attempt) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var state by _connectionState::value
    private var desired = emptySet<String>()
    private var current: Attempt? = null
    private var openedAt: Long? = null
    private var failures = 0
    private var retryJob: Job? = null
    private var watchdogJob: Job? = null

    init {
        scope.launch { for (event in events) handle(event) }
    }

    /** An empty set closes the connection (`Idle`); a non-empty one opens it if needed. */
    fun setDesiredStreams(streams: Set<String>) {
        events.trySend(Event.DesiredStreams(streams))
    }

    /** Forwarded from the default network callback's `onAvailable`. */
    fun onNetworkAvailable() {
        events.trySend(Event.NetworkAvailable)
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.DesiredStreams -> {
                desired = event.streams
                if (desired.isEmpty()) {
                    enterIdle()
                } else if (state == Idle) {
                    state = Connecting
                    connect()
                }
            }
            Event.NetworkAvailable -> if (state == Connecting || state == Reconnecting) {
                failures = 0
                if (current == null) {
                    retryJob?.cancel()
                    connect()
                }
            }
            Event.RetryDue -> if (current == null && (state == Connecting || state == Reconnecting)) connect()
            is Event.Opened -> if (event.attempt === current) {
                state = Connected
                openedAt = clock()
                restartWatchdog()
            }
            is Event.Message -> if (event.attempt === current) onMessage(event.text)
            is Event.Closed -> if (event.attempt === current) connectionLost("closed")
            is Event.WatchdogExpired -> if (event.attempt === current && state == Connected) {
                connectionLost("no message for 10 s")
            }
        }
    }

    private fun onMessage(text: String) {
        restartWatchdog()
        when (val message = parseStreamMessage(text)) {
            is StreamMessage.TickerUpdate -> _ticker.value = Ticker(message.lastPrice, message.changePercent, clock())
            is StreamMessage.ControlResponse -> if (!message.success) connectionLost("subscription failed: $text")
            is StreamMessage.KlineUpdate, null -> Unit
        }
    }

    private fun connect() {
        val attempt = Attempt()
        current = attempt
        attempt.connection = connector.connect(
            STREAM_URL + desired.sorted().joinToString("/"),
            object : WebSocketConnector.Listener {
                override fun onOpen() {
                    events.trySend(Event.Opened(attempt))
                }

                override fun onMessage(text: String) {
                    events.trySend(Event.Message(attempt, text))
                }

                override fun onClosed() {
                    events.trySend(Event.Closed(attempt))
                }
            },
        )
    }

    private fun connectionLost(reason: String) {
        Log.w(TAG, "Connection lost: $reason")
        current?.connection?.cancel()
        current = null
        watchdogJob?.cancel()
        val upFor = openedAt?.let { clock() - it }
        // A connection that drops before 60 s counts as a failed attempt.
        failures = if (upFor != null && upFor >= STABLE_CONNECTION_MILLIS) 0 else failures + 1
        openedAt = null
        if (state == Connected) state = Reconnecting
        if (failures == 0) {
            connect()
        } else {
            val wait = backoffDelayMillis(failures, random)
            retryJob = scope.launch {
                delay(wait)
                events.send(Event.RetryDue)
            }
        }
    }

    private fun enterIdle() {
        retryJob?.cancel()
        watchdogJob?.cancel()
        current?.connection?.close()
        current = null
        openedAt = null
        failures = 0
        state = Idle
    }

    private fun restartWatchdog() {
        val attempt = current ?: return
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(WATCHDOG_MILLIS)
            events.send(Event.WatchdogExpired(attempt))
        }
    }
}
