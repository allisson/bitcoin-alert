package io.github.allisson.bitcoinalert

import io.github.allisson.bitcoinalert.data.binance.WebSocketConnector

/** The real wiring with a WebSocket connector that never opens a connection. */
class TestApp : BitcoinAlertApp() {
    override fun webSocketConnector() = object : WebSocketConnector {
        override fun connect(url: String, listener: WebSocketConnector.Listener) = object : WebSocketConnector.Connection {
            override fun send(text: String) = true
            override fun close() = Unit
            override fun cancel() = Unit
        }
    }
}
