package io.github.allisson.bitcoinalert.ui.home

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

private val EN_US = Locale.forLanguageTag("en-US")
private val PT_BR = Locale.forLanguageTag("pt-BR")

class FormattingTest {
    @Test
    fun `price uses the locale grouping and exactly 2 decimal places`() {
        assertEquals("104,250.50", formatPrice(BigDecimal("104250.50"), EN_US))
        assertEquals("104.250,50", formatPrice(BigDecimal("104250.50"), PT_BR))
        assertEquals("0.01", formatPrice(BigDecimal("0.01"), EN_US))
    }

    @Test
    fun `negative change shows sign and down arrow with 2 decimals`() {
        assertEquals(ChangeDisplay("▼ -3.83%", ChangeDirection.Down), formatChange(BigDecimal("-3.829"), EN_US))
        assertEquals(ChangeDisplay("▼ -3,83%", ChangeDirection.Down), formatChange(BigDecimal("-3.829"), PT_BR))
    }

    @Test
    fun `positive change rounds half up and shows sign and up arrow`() {
        assertEquals(ChangeDisplay("▲ +1.01%", ChangeDirection.Up), formatChange(BigDecimal("1.005"), EN_US))
        assertEquals(ChangeDisplay("▲ +0.01%", ChangeDirection.Up), formatChange(BigDecimal("0.005"), EN_US))
        assertEquals(ChangeDisplay("▲ +1234.50%", ChangeDirection.Up), formatChange(BigDecimal("1234.5"), EN_US))
    }

    @Test
    fun `change that rounds to zero shows no sign and no arrow`() {
        assertEquals(ChangeDisplay("0.00%", ChangeDirection.Flat), formatChange(BigDecimal("-0.004"), EN_US))
        assertEquals(ChangeDisplay("0,00%", ChangeDirection.Flat), formatChange(BigDecimal("0.000"), PT_BR))
    }
}
