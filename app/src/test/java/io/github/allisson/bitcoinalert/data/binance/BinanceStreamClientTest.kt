package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Connected
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Connecting
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Idle
import io.github.allisson.bitcoinalert.domain.model.ConnectionState.Reconnecting
import io.github.allisson.bitcoinalert.domain.model.Ticker
import java.math.BigDecimal
import kotlin.random.Random
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TICKER =
    """{"stream":"btcusdt@ticker","data":{"e":"24hrTicker","c":"100000.12000000","P":"-3.829"}}"""

private val TICKER_ONLY = setOf("btcusdt@ticker")
private val HOME_5M = setOf("btcusdt@ticker", "btcusdt@kline_5m")
private val HOME_15M = setOf("btcusdt@ticker", "btcusdt@kline_15m")
private val HOME_1H = setOf("btcusdt@ticker", "btcusdt@kline_1h")

private fun kline(interval: String) =
    """{"stream":"btcusdt@kline_$interval","data":{"k":{"t":1700000000000,"i":"$interval",""" +
        """"o":"1.00","h":"1.00","l":"1.00","c":"1.00","v":"1.0"}}}"""

private fun request(method: String, stream: String, id: Int) = """{"method":"$method","params":["$stream"],"id":$id}"""

@OptIn(ExperimentalCoroutinesApi::class)
class BinanceStreamClientTest {

    private class FakeConnection(val url: String, val listener: WebSocketConnector.Listener) :
        WebSocketConnector.Connection {
        val sent = mutableListOf<String>()
        var closed = false
        var cancelled = false
        override fun send(text: String) = sent.add(text)

        override fun close() {
            closed = true
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private val attempts = mutableListOf<FakeConnection>()
    private val connector = object : WebSocketConnector {
        override fun connect(url: String, listener: WebSocketConnector.Listener) =
            FakeConnection(url, listener).also { attempts += it }
    }
    private var jitter = 0.0
    private val random = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = jitter
    }
    private val last get() = attempts.last()

    private fun TestScope.client(streams: Set<String> = setOf("btcusdt@ticker")) =
        BinanceStreamClient(connector, backgroundScope, { testScheduler.currentTime }, random).also {
            it.setDesiredStreams(streams)
            runCurrent()
        }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.open() {
        last.listener.onOpen()
        runCurrent()
    }

    private fun TestScope.message(text: String = TICKER) {
        last.listener.onMessage(text)
        runCurrent()
    }

    private fun TestScope.fail() {
        last.listener.onClosed()
        runCurrent()
    }

    /** Advances [millis] with a message every 5 s, so the watchdog stays quiet. */
    private fun TestScope.keepAlive(millis: Long) = repeat((millis / 5_000).toInt()) {
        advance(5_000)
        message()
    }

    @Test
    fun `backoff base doubles per consecutive failure and caps at 60 s`() {
        assertEquals(
            listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000, 60_000),
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 40).map { backoffDelayMillis(it, random) },
        )
    }

    @Test
    fun `jitter only adds, up to 20 percent of the base`() {
        jitter = 0.5
        assertEquals(1_100, backoffDelayMillis(1, random))
        jitter = 0.999_999_9
        assertEquals(71_999, backoffDelayMillis(7, random))
    }

    @Test
    fun `starts Idle, and a stream set connects with its streams in the url`() = runTest {
        val client = BinanceStreamClient(connector, backgroundScope, { testScheduler.currentTime }, random)
        runCurrent()
        assertEquals(Idle, client.connectionState.value)
        assertTrue(attempts.isEmpty())

        client.setDesiredStreams(setOf("btcusdt@ticker", "btcusdt@kline_1h"))
        runCurrent()
        assertEquals(Connecting, client.connectionState.value)
        assertEquals("wss://stream.binance.com:443/stream?streams=btcusdt@kline_1h/btcusdt@ticker", last.url)

        open()
        assertEquals(Connected, client.connectionState.value)
    }

    @Test
    fun `ticker message updates the ticker with the monotonic receive time`() = runTest {
        val client = client()
        open()
        assertNull(client.ticker.value)

        advance(1_234)
        message()

        assertEquals(Ticker(BigDecimal("100000.12"), BigDecimal("-3.829"), 1_234), client.ticker.value)
    }

    @Test
    fun `consecutive failures before opening wait the backoff and stay Connecting`() = runTest {
        val client = client()

        fail()
        assertEquals(Connecting, client.connectionState.value)
        advance(999)
        assertEquals(1, attempts.size)
        advance(1)
        assertEquals(2, attempts.size)

        fail()
        advance(1_999)
        assertEquals(2, attempts.size)
        advance(1)
        assertEquals(3, attempts.size)
        assertEquals(Connecting, client.connectionState.value)
    }

    @Test
    fun `a connection that drops before 60 s counts as a failed attempt`() = runTest {
        val client = client()
        fail()
        advance(1_000)
        open()
        keepAlive(55_000)

        fail()

        assertEquals(Reconnecting, client.connectionState.value)
        advance(1_999)
        assertEquals(2, attempts.size)
        advance(1)
        assertEquals(3, attempts.size)
    }

    @Test
    fun `a connection up for 60 s reconnects without delay, like the 24-hour close`() = runTest {
        val client = client()
        fail()
        advance(1_000)
        open()
        keepAlive(60_000)

        fail()

        assertEquals(Reconnecting, client.connectionState.value)
        assertEquals(3, attempts.size)
        fail()
        advance(999)
        assertEquals(3, attempts.size)
        advance(1)
        assertEquals(4, attempts.size)
    }

    @Test
    fun `watchdog reconnects after 10 s without messages while Connected`() = runTest {
        val client = client()
        open()
        keepAlive(20_000)

        advance(9_999)
        assertFalse(last.cancelled)
        advance(1)

        assertTrue(last.cancelled)
        assertEquals(Reconnecting, client.connectionState.value)
        advance(1_000)
        assertEquals(2, attempts.size)
    }

    @Test
    fun `watchdog does not run while Connecting`() = runTest {
        client()

        advance(120_000)

        assertEquals(1, attempts.size)
        assertFalse(last.cancelled)
    }

    @Test
    fun `network availability grants one immediate attempt, then failures wait again`() = runTest {
        val client = client()
        fail()
        advance(1_000)
        fail() // waiting 2 s

        client.onNetworkAvailable()
        runCurrent()
        assertEquals(3, attempts.size)

        fail()
        advance(999)
        assertEquals(3, attempts.size)
        advance(1)
        assertEquals(4, attempts.size)
    }

    @Test
    fun `network availability does nothing while Connected or Idle`() = runTest {
        val client = BinanceStreamClient(connector, backgroundScope, { testScheduler.currentTime }, random)
        client.onNetworkAvailable()
        runCurrent()
        assertTrue(attempts.isEmpty())

        client.setDesiredStreams(setOf("btcusdt@ticker"))
        runCurrent()
        open()
        client.onNetworkAvailable()
        runCurrent()

        assertEquals(1, attempts.size)
        assertFalse(last.cancelled)
        assertEquals(Connected, client.connectionState.value)
    }

    @Test
    fun `empty stream set closes the connection and nothing reconnects while Idle`() = runTest {
        val client = client()
        open()

        client.setDesiredStreams(emptySet())
        runCurrent()

        assertTrue(last.closed)
        assertEquals(Idle, client.connectionState.value)
        last.listener.onClosed()
        client.onNetworkAvailable()
        advance(120_000)
        assertEquals(1, attempts.size)
    }

    @Test
    fun `leaving Idle connects without delay even after failures`() = runTest {
        val client = client()
        fail()
        advance(1_000)
        fail() // waiting 2 s
        client.setDesiredStreams(emptySet())
        runCurrent()

        client.setDesiredStreams(setOf("btcusdt@ticker"))
        runCurrent()

        assertEquals(3, attempts.size)
        assertEquals(Connecting, client.connectionState.value)

        // Entering Idle reset the counter: the next failure is the first one again.
        fail()
        advance(999)
        assertEquals(3, attempts.size)
        advance(1)
        assertEquals(4, attempts.size)
    }

    @Test
    fun `failed subscription response is treated as a lost connection`() = runTest {
        val client = client()
        open()

        message("""{"code":0,"msg":"Unknown property","id":1}""")

        assertTrue(last.cancelled)
        assertEquals(Reconnecting, client.connectionState.value)
    }

    @Test
    fun `events from a replaced connection are ignored`() = runTest {
        val client = client()
        open()
        advance(10_000) // watchdog replaces it
        val stale = attempts.first()
        advance(1_000)
        assertEquals(2, attempts.size)

        stale.listener.onOpen()
        stale.listener.onMessage(TICKER)
        stale.listener.onClosed()
        runCurrent()

        assertEquals(Reconnecting, client.connectionState.value)
        assertNull(client.ticker.value)
        assertEquals(2, attempts.size)
    }

    private fun TestScope.collectKlines(client: BinanceStreamClient) = mutableListOf<KlineEvent>().also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { client.klineEvents.toList(it) }
    }

    @Test
    fun `stream set changes while Connecting send nothing and the next attempt uses the latest set`() = runTest {
        val client = client(HOME_1H)

        client.setDesiredStreams(HOME_15M)
        runCurrent()
        assertTrue(last.sent.isEmpty())
        fail()
        advance(1_000)

        assertEquals("wss://stream.binance.com:443/stream?streams=btcusdt@kline_15m/btcusdt@ticker", last.url)
    }

    @Test
    fun `after onOpen only the difference from the url's set is sent`() = runTest {
        val client = client(HOME_1H)
        client.setDesiredStreams(TICKER_ONLY)
        runCurrent()

        open()

        assertEquals(listOf(request("UNSUBSCRIBE", "btcusdt@kline_1h", 1)), last.sent)
    }

    @Test
    fun `first change after a quiet second goes out now, then at most one pair per second with the latest set`() =
        runTest {
            val client = client(TICKER_ONLY)
            open()

            client.setDesiredStreams(HOME_1H)
            runCurrent()
            assertEquals(listOf(request("SUBSCRIBE", "btcusdt@kline_1h", 1)), last.sent)

            client.setDesiredStreams(HOME_15M)
            client.setDesiredStreams(HOME_5M)
            advance(999)
            assertEquals(1, last.sent.size)
            advance(1)

            assertEquals(
                listOf(
                    request("SUBSCRIBE", "btcusdt@kline_1h", 1),
                    request("UNSUBSCRIBE", "btcusdt@kline_1h", 2),
                    request("SUBSCRIBE", "btcusdt@kline_5m", 3),
                ),
                last.sent,
            )
        }

    @Test
    fun `a window that ends with no difference sends nothing, and the next change goes out now`() = runTest {
        val client = client(TICKER_ONLY)
        open()
        client.setDesiredStreams(HOME_1H)
        client.setDesiredStreams(TICKER_ONLY)
        client.setDesiredStreams(HOME_1H)
        runCurrent()

        advance(1_000)
        assertEquals(1, last.sent.size)

        client.setDesiredStreams(TICKER_ONLY)
        runCurrent()
        assertEquals(request("UNSUBSCRIBE", "btcusdt@kline_1h", 2), last.sent.last())
    }

    @Test
    fun `an unchanged stream set sends nothing`() = runTest {
        val client = client(HOME_1H)
        open()

        client.setDesiredStreams(HOME_1H)
        advance(2_000)

        assertTrue(last.sent.isEmpty())
    }

    @Test
    fun `a kline in the url is in effect on onOpen, not while Connecting`() = runTest {
        val client = client(HOME_1H)
        val events = collectKlines(client)
        assertTrue(events.isEmpty())

        open()

        assertEquals(listOf(KlineEvent.InEffect(Timeframe.H1)), events)
    }

    @Test
    fun `a reconnection without a kline stream loads nothing`() = runTest {
        val client = client(TICKER_ONLY)
        val events = collectKlines(client)
        open()
        fail()
        advance(1_000)
        open()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a subscribed kline is in effect only once its SUBSCRIBE succeeds`() = runTest {
        val client = client(TICKER_ONLY)
        open()
        val events = collectKlines(client)

        client.setDesiredStreams(HOME_1H)
        runCurrent()
        assertTrue(events.isEmpty())

        message("""{"result":null,"id":1}""")
        assertEquals(listOf(KlineEvent.InEffect(Timeframe.H1)), events)
    }

    @Test
    fun `klines of a stream no longer desired are discarded`() = runTest {
        val client = client(HOME_1H)
        open()
        val events = collectKlines(client)

        client.setDesiredStreams(HOME_15M)
        runCurrent()
        message(kline("1h"))
        message(kline("15m"))

        val candle = Candle(1700000000000, BigDecimal("1.00"), BigDecimal("1.00"), BigDecimal("1.00"), BigDecimal("1.00"), BigDecimal("1.0"))
        assertEquals(
            listOf(KlineEvent.Removed(Timeframe.H1), KlineEvent.Update(Timeframe.M15, candle)),
            events,
        )
    }
}
