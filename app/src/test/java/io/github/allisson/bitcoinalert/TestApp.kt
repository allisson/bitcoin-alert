package io.github.allisson.bitcoinalert

import io.github.allisson.bitcoinalert.data.binance.WebSocketConnector

/** The real wiring with a WebSocket connector that never opens a connection; tests feed [listener]. */
class TestApp : BitcoinAlertApp() {
    @Volatile
    var listener: WebSocketConnector.Listener? = null

    override fun webSocketConnector() = object : WebSocketConnector {
        override fun connect(url: String, listener: WebSocketConnector.Listener): WebSocketConnector.Connection {
            this@TestApp.listener = listener
            return object : WebSocketConnector.Connection {
                override fun send(text: String) = true
                override fun close() = Unit
                override fun cancel() = Unit
            }
        }
    }
}
