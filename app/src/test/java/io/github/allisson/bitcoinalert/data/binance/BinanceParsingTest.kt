package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.data.binance.StreamMessage.ControlResponse
import io.github.allisson.bitcoinalert.data.binance.StreamMessage.KlineUpdate
import io.github.allisson.bitcoinalert.data.binance.StreamMessage.TickerUpdate
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.math.BigDecimal
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BinanceParsingTest {

    private fun ticker(lastPrice: String, change: String = "-3.829") =
        """{"stream":"btcusdt@ticker","data":{"e":"24hrTicker","E":1,"s":"BTCUSDT","p":"-10.00",""" +
            """"P":"$change","C":2,"c":"$lastPrice","v":"1.5"}}"""

    private fun kline(interval: String, open: String = "100000.10000000") =
        """{"stream":"btcusdt@kline_$interval","data":{"e":"kline","E":1,"s":"BTCUSDT","k":""" +
            """{"t":1700000000000,"T":1700003599999,"s":"BTCUSDT","i":"$interval","o":"$open",""" +
            """"c":"100100.00000000","h":"100200.50000000","l":"99900.00000000","v":"336.78983000","x":false,"L":9}}}"""

    @Test
    fun `ticker price is normalized to scale 2 and the 24-hour change is kept as sent`() {
        val message = parseStreamMessage(ticker("100000.12000000")) as TickerUpdate

        assertEquals(BigDecimal("100000.12"), message.lastPrice)
        assertEquals(2, message.lastPrice.scale())
        assertEquals(BigDecimal("-3.829"), message.changePercent)
    }

    @Test
    fun `normalized prices compare equal across scales with compareTo`() {
        val price = (parseStreamMessage(ticker("100000.50000000")) as TickerUpdate).lastPrice

        assertEquals(0, price.compareTo(BigDecimal("100000.5")))
        assertEquals(0, price.compareTo(BigDecimal("100000.500000")))
    }

    @Test
    fun `ticker with a price finer than the tick size is discarded`() {
        assertNull(parseStreamMessage(ticker("100000.12300000")))
    }

    @Test
    fun `kline is parsed with normalized prices and unnormalized volume`() {
        val message = parseStreamMessage(kline("1h")) as KlineUpdate

        assertEquals("btcusdt@kline_1h", message.stream)
        assertEquals(Timeframe.H1, message.timeframe)
        with(message.candle) {
            assertEquals(1700000000000, openTime)
            assertEquals(BigDecimal("100000.10"), open)
            assertEquals(BigDecimal("100200.50"), high)
            assertEquals(BigDecimal("99900.00"), low)
            assertEquals(BigDecimal("100100.00"), close)
            assertEquals(BigDecimal("336.78983000"), volume)
        }
    }

    @Test
    fun `kline intervals map to every timeframe`() {
        Timeframe.entries.forEach {
            assertEquals(it, (parseStreamMessage(kline(it.binanceInterval)) as KlineUpdate).timeframe)
        }
    }

    @Test
    fun `kline with a price finer than the tick size or an unknown interval is discarded`() {
        assertNull(parseStreamMessage(kline("1h", open = "100000.001")))
        assertNull(parseStreamMessage(kline("1m")))
    }

    @Test
    fun `subscribe success response is recognized`() {
        assertEquals(ControlResponse(id = 1, success = true), parseStreamMessage("""{"result":null,"id":1}"""))
    }

    @Test
    fun `non-null result and error responses are failures, with or without id`() {
        assertEquals(ControlResponse(2, false), parseStreamMessage("""{"result":["btcusdt@ticker"],"id":2}"""))
        assertEquals(ControlResponse(1, false), parseStreamMessage("""{"code":0,"msg":"Unknown property","id":1}"""))
        assertEquals(ControlResponse(null, false), parseStreamMessage("""{"code":3,"msg":"Invalid JSON"}"""))
    }

    @Test
    fun `malformed and unknown messages are discarded`() {
        assertNull(parseStreamMessage("not json"))
        assertNull(parseStreamMessage("[1,2]"))
        assertNull(parseStreamMessage("""{"stream":"btcusdt@trade","data":{}}"""))
        assertNull(parseStreamMessage("""{"stream":"btcusdt@ticker","data":{"c":"1.00"}}"""))
    }

    @Test
    fun `klines response is parsed`() {
        val candles = parseKlines(
            """[[1700000000000,"100000.10000000","100200.50000000","99900.00000000","100100.00000000",""" +
                """"336.78983000",1700003599999,"0",1,"0","0","0"],""" +
                """[1700003600000,"100100.00000000","100100.00000000","100000.00000000","100050.00000000",""" +
                """"1.00000000",1700007199999,"0",1,"0","0","0"]]""",
        )

        assertEquals(listOf(1700000000000, 1700003600000), candles.map { it.openTime })
        assertEquals(BigDecimal("100050.00"), candles[1].close)
        assertEquals(BigDecimal("336.78983000"), candles[0].volume)
    }

    @Test
    fun `invalid klines responses fail the whole load`() {
        listOf(
            """{"code":-1121,"msg":"Invalid symbol."}""",
            """[[1700000000000,"1.00"]]""",
            """[[1700000000000,"1.001","1.00","1.00","1.00","1.0"]]""",
            """[["x","1.00","1.00","1.00","1.00","1.0"]]""",
        ).forEach { body ->
            val e = assertThrows(SerializationException::class.java) { parseKlines(body) }
            assertTrue(body, e.message!!.isNotEmpty())
        }
    }
}
