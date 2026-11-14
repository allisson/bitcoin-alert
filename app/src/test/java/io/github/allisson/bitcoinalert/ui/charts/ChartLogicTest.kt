package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.BELOW
import io.github.allisson.bitcoinalert.domain.model.Candle
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val EN_US = Locale.forLanguageTag("en-US")
private val PT_BR = Locale.forLanguageTag("pt-BR")

class ChartLogicTest {
    @Test
    fun `an alert inside the range has no pin, outside it pins to that edge`() {
        assertNull(pinOf(95_000.0, 94_000.0, 98_000.0))
        assertNull(pinOf(94_000.0, 94_000.0, 98_000.0))
        assertNull(pinOf(98_000.0, 94_000.0, 98_000.0))
        assertEquals(Pin.Top, pinOf(98_000.01, 94_000.0, 98_000.0))
        assertEquals(Pin.Bottom, pinOf(93_999.99, 94_000.0, 98_000.0))
    }

    @Test
    fun `alert label shows the arrow, the target, the signed distance and the pin`() {
        val price = BigDecimal("97412.35")
        assertEquals("▲ 102,000  +4.71%", alertLabel(ABOVE, BigDecimal("102000.00"), price, null, EN_US))
        assertEquals("▼ 95.150  -2,32% ↓", alertLabel(BELOW, BigDecimal("95150.00"), price, Pin.Bottom, PT_BR))
        assertEquals("▲ 102,000 ↑", alertLabel(ABOVE, BigDecimal("102000.00"), null, Pin.Top, EN_US))
    }

    @Test
    fun `candle change goes from the open to the close`() {
        fun candle(open: String, close: String) =
            Candle(0, BigDecimal(open), BigDecimal(close), BigDecimal(open), BigDecimal(close), BigDecimal.ONE)
        assertEquals(BigDecimal("1.00"), candleChange(candle("100.00", "101.00")))
        assertEquals(BigDecimal("-0.11"), candleChange(candle("95689.30", "95585.86")))
    }
}
