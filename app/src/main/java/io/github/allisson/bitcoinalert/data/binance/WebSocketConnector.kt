package io.github.allisson.bitcoinalert.data.binance

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Opens WebSocket connections; the seam that lets [BinanceStreamClient] tests use a fake. */
interface WebSocketConnector {
    fun connect(url: String, listener: Listener): Connection

    /** Called on OkHttp threads. [onClosed] may be called more than once per connection. */
    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        fun onClosed()
    }

    interface Connection {
        /** Graceful close, for a deliberate disconnect. */
        fun close()

        /** Immediate teardown, for a connection considered lost. */
        fun cancel()
    }
}

class OkHttpWebSocketConnector(private val httpClient: OkHttpClient) : WebSocketConnector {
    override fun connect(url: String, listener: WebSocketConnector.Listener): WebSocketConnector.Connection {
        val webSocket = httpClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)

                // Server-initiated close, e.g. Binance's 24-hour limit.
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    listener.onClosed()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("Binance", "WebSocket failure", t)
                    listener.onClosed()
                }
            },
        )
        return object : WebSocketConnector.Connection {
            override fun close() {
                webSocket.close(1000, null)
            }

            override fun cancel() = webSocket.cancel()
        }
    }
}
