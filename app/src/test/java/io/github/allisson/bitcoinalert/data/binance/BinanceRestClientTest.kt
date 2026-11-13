package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BinanceRestClientTest {

    private val server = MockWebServer()
    private val canceled = CountDownLatch(1)
    private val httpClient = OkHttpClient.Builder()
        .eventListener(object : EventListener() {
            override fun canceled(call: Call) = canceled.countDown()
        })
        .build()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val client get() = BinanceRestClient(httpClient, server.url("/"))

    @Test
    fun `requests 200 BTCUSDT klines of the timeframe and parses them`() = runBlocking {
        server.enqueue(
            MockResponse(
                body = """[[1700000000000,"100000.10000000","100200.50000000","99900.00000000",""" +
                    """"100100.00000000","336.78983000",1700003599999,"0",1,"0","0","0"]]""",
            ),
        )

        val candles = client.klines(Timeframe.M15)

        assertEquals(1, candles.size)
        val url = server.takeRequest().url
        assertEquals("/api/v3/klines", url.encodedPath)
        assertEquals("BTCUSDT", url.queryParameter("symbol"))
        assertEquals("15m", url.queryParameter("interval"))
        assertEquals("200", url.queryParameter("limit"))
    }

    @Test
    fun `non-2xx response throws BinanceHttpException with the status code`() {
        server.enqueue(MockResponse(code = 429, body = """{"code":-1003,"msg":"Too many requests"}"""))

        val e = assertThrows(BinanceHttpException::class.java) { runBlocking { client.klines(Timeframe.H1) } }
        assertEquals(429, e.code)
    }

    @Test
    fun `invalid body throws SerializationException`() {
        server.enqueue(MockResponse(body = "<html>"))

        assertThrows(SerializationException::class.java) { runBlocking { client.klines(Timeframe.H1) } }
    }

    @Test
    fun `cancelling the coroutine cancels the HTTP call`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").headersDelay(10, TimeUnit.SECONDS).build())

        val load = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { client.klines(Timeframe.D1) }
        server.takeRequest()
        load.cancel()

        assertTrue(canceled.await(5, TimeUnit.SECONDS))
        assertTrue(load.isCancelled)
    }
}
