package io.github.allisson.bitcoinalert.domain.model

enum class Timeframe(val binanceInterval: String) {
    M5("5m"),
    M15("15m"),
    H1("1h"),
    D1("1d"),
}
