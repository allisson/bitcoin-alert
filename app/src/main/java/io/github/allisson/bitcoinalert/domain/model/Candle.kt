package io.github.allisson.bitcoinalert.domain.model

import java.math.BigDecimal

/** Prices have scale 2; volume keeps the scale Binance sends. [openTime] is epoch milliseconds. */
data class Candle(
    val openTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
)
