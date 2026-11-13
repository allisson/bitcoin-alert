package io.github.allisson.bitcoinalert.domain.model

import java.math.BigDecimal

/**
 * [lastPrice] has scale 2; [changePercent] is the 24-hour change as Binance sends it.
 * [receivedAt] is monotonic time (elapsedRealtime) in milliseconds, used for stale data.
 */
data class Ticker(
    val lastPrice: BigDecimal,
    val changePercent: BigDecimal,
    val receivedAt: Long,
)
