package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

private val EN_US = Locale.forLanguageTag("en-US")
private val PT_BR = Locale.forLanguageTag("pt-BR")
private val SAO_PAULO = ZoneId.of("America/Sao_Paulo")

// 2026-11-13T00:00:00Z
private const val MIDNIGHT_UTC = 1_794_528_000_000L

class ChartFormattingTest {
    @Test
    fun `volume drops trailing zeros and uses the locale`() {
        assertEquals("336.78983", formatVolume(BigDecimal("336.78983000"), EN_US))
        assertEquals("1.234,5", formatVolume(BigDecimal("1234.50000000"), PT_BR))
        assertEquals("0.00000001", formatVolume(BigDecimal("0.00000001"), EN_US))
        assertEquals("1,200", formatVolume(BigDecimal("1200.00000000"), EN_US))
        assertEquals("0", formatVolume(BigDecimal("0.00000000"), EN_US))
    }

    @Test
    fun `axis price has grouping and no decimals`() {
        assertEquals("97,124", formatAxisPrice(97_123.5, EN_US))
        assertEquals("97.123", formatAxisPrice(97_123.4, PT_BR))
    }

    @Test
    fun `axis time is HH mm in the device zone for intraday and the short date for 1d`() {
        assertEquals("00:00", formatAxisTime(MIDNIGHT_UTC, Timeframe.H1, ZoneOffset.UTC, EN_US))
        assertEquals("21:00", formatAxisTime(MIDNIGHT_UTC, Timeframe.M5, SAO_PAULO, PT_BR))
        assertEquals("12/11/2026", formatAxisTime(MIDNIGHT_UTC, Timeframe.D1, SAO_PAULO, PT_BR))
        assertEquals("11/13/26", formatAxisTime(MIDNIGHT_UTC, Timeframe.D1, ZoneOffset.UTC, EN_US))
    }

    @Test
    fun `selected candle time has date and time for intraday and only the date for 1d`() {
        assertEquals("12/11/2026 21:00", formatCandleTime(MIDNIGHT_UTC, Timeframe.M15, SAO_PAULO, PT_BR))
        assertEquals("12/11/2026", formatCandleTime(MIDNIGHT_UTC, Timeframe.D1, SAO_PAULO, PT_BR))
    }
}
