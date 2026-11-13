package io.github.allisson.bitcoinalert.data.binance

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpWebSocketConnectorTest {

    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.close()

    @Test
    fun `delivers open, messages and a server close from a real websocket`() {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"result":null,"id":1}""")
                    webSocket.close(1000, "bye")
                }
            }).build(),
        )
        val events = LinkedBlockingQueue<String>()

        OkHttpWebSocketConnector(OkHttpClient()).connect(
            server.url("/stream?streams=btcusdt@ticker").toString(),
            object : WebSocketConnector.Listener {
                override fun onOpen() {
                    events += "open"
                }

                override fun onMessage(text: String) {
                    events += "message ${parseStreamMessage(text)}"
                }

                override fun onClosed() {
                    events += "closed"
                }
            },
        )

        assertEquals("open", events.poll(5, TimeUnit.SECONDS))
        assertEquals("message ControlResponse(id=1, success=true)", events.poll(5, TimeUnit.SECONDS))
        assertEquals("closed", events.poll(5, TimeUnit.SECONDS))
        assertEquals("btcusdt@ticker", server.takeRequest().url.queryParameter("streams"))
    }
}
