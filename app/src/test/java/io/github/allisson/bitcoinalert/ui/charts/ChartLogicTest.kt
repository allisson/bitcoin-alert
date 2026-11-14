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
    fun `a long press becomes a whole dollar target, above or below the current price`() {
        val price = BigDecimal("96000.00")
        // Axis from 94,000 at y = 400 to 98,000 at y = 0: 10 px per $100.
        fun at(y: Float) = alertDraftAt(y, 0f, 400f, 94_000.0, 98_000.0, price)
        assertEquals(AlertDraft(ABOVE, BigDecimal("98000.00")), at(0f))
        assertEquals(AlertDraft(BELOW, BigDecimal("94000.00")), at(400f))
        assertEquals(AlertDraft(ABOVE, BigDecimal("97000.00")), at(100f))
        assertEquals(AlertDraft(BELOW, BigDecimal("95500.00")), at(250f))
        assertEquals(AlertDraft(ABOVE, BigDecimal("96000.00")), at(200f))
        assertEquals(AlertDraft(BELOW, BigDecimal("95999.00")), at(200.1f))
        assertEquals(AlertDraft(ABOVE, BigDecimal("96001.00")), at(199.95f))
        assertNull(at(-1f))
        assertNull(at(401f))
    }

    @Test
    fun `alert labels stack without touching, inside the area, in their order`() {
        // Labels 10 px tall, 2 px apart, in an area from 0 to 100: centers from 5 to 95.
        fun stack(vararg y: Float) = stackLabels(y.toList(), 10f, 2f, 0f, 100f)
        assertEquals(emptyList<Float>(), stack())
        assertEquals(listOf(20f, 50f), stack(20f, 50f))
        assertEquals(listOf(50f, 62f), stack(50f, 55f))
        assertEquals(listOf(62f, 50f), stack(55f, 50f))
        assertEquals(listOf(5f, 17f), stack(5f, 5f))
        assertEquals(listOf(83f, 95f), stack(95f, 95f))
        assertEquals(listOf(71f, 83f, 95f), stack(90f, 94f, 95f))
        assertEquals(listOf(5f), stack(-10f))
        assertEquals((0..9).map { 5f + it * 10 }, stack(*FloatArray(10) { 50f }))
    }

    @Test
    fun `candle change goes from the open to the close`() {
        fun candle(open: String, close: String) =
            Candle(0, BigDecimal(open), BigDecimal(close), BigDecimal(open), BigDecimal(close), BigDecimal.ONE)
        assertEquals(BigDecimal("1.00"), candleChange(candle("100.00", "101.00")))
        assertEquals(BigDecimal("-0.11"), candleChange(candle("95689.30", "95585.86")))
    }
}
