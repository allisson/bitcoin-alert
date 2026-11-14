package io.github.allisson.bitcoinalert.ui.alerts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.BELOW
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertsLogicTest {
    @Test
    fun `valid targets parse to scale 2`() {
        mapOf(
            "100000.50" to "100000.50",
            "100000,50" to "100000.50",
            " 100.50 " to "100.50",
            "0100" to "100.00",
            "100" to "100.00",
            "0.01" to "0.01",
            "1.5" to "1.50",
        ).forEach { (input, expected) -> assertEquals(input, expected, parseTargetPrice(input)?.toPlainString()) }
    }

    @Test
    fun `invalid targets are rejected`() {
        listOf(
            "100.000,50", "100,000.50", "1.2.3", "1,000", "1.000", "100,000", "100.001",
            "0", "0.00", ".50", "100.", "", " ", "-5", "+5", "1e5", "abc", "1 000",
        ).forEach { assertNull(it, parseTargetPrice(it)) }
    }

    private fun price(text: String) = BigDecimal(text)

    @Test
    fun `percent from price is signed and rounded HALF_UP`() {
        assertEquals("2.66", percentFromPrice(price("100000.00"), price("97412.35")).toPlainString())
        assertEquals("-5.04", percentFromPrice(price("92500.00"), price("97412.35")).toPlainString())
        assertEquals("0.01", percentFromPrice(price("100.005"), price("100.00")).toPlainString())
    }

    @Test
    fun `distance on the waiting side fills the bar toward the target`() {
        val above = distance(ABOVE, price("100000.00"), price("97412.35")) as Distance.Away
        assertEquals("2.66", above.percent.toPlainString())
        assertEquals(0.734f, above.fill, 0.0001f)
        val below = distance(BELOW, price("92500.00"), price("97412.35")) as Distance.Away
        assertEquals("5.04", below.percent.toPlainString())
        assertEquals(1f, (distance(ABOVE, price("100.00"), price("100.00")) as Distance.Away).fill, 0f)
        assertEquals(0f, (distance(ABOVE, price("120.00"), price("100.00")) as Distance.Away).fill, 0f)
    }

    @Test
    fun `a price past the target is Beyond`() {
        assertEquals(Distance.Beyond, distance(ABOVE, price("90000.00"), price("97000.00")))
        assertEquals(Distance.Beyond, distance(BELOW, price("100000.00"), price("97000.00")))
    }

    private fun alert(id: String, createdAt: Long, enabled: Boolean = true, triggeredAt: Long? = null, direction: AlertDirection = ABOVE) =
        PriceAlert(id, price("100.00"), direction, enabled, triggeredAt, createdAt, activationCycle = 0)

    @Test
    fun `sections split by state and order Triggered by triggeredAt DESC, id DESC`() {
        // Input order is the query's createdAt DESC.
        val alerts = listOf(
            alert("e", createdAt = 5, triggeredAt = 10),
            alert("d", createdAt = 4),
            alert("c", createdAt = 3, enabled = false, triggeredAt = 20),
            alert("b", createdAt = 2, triggeredAt = 30),
            alert("a", createdAt = 1, triggeredAt = 10),
            alert("z", createdAt = 0, enabled = false),
        )
        val history = listOf(
            AlertHistoryEntry(1, "b", price("100.00"), price("100.01"), ABOVE, 30),
            AlertHistoryEntry(2, "a", price("100.00"), price("100.02"), ABOVE, 10),
            AlertHistoryEntry(3, "e", price("100.00"), price("100.03"), ABOVE, 9),
        )
        val sections = alertSections(alerts, history)
        assertEquals(listOf("d"), sections.active.map { it.id })
        assertEquals(listOf("b", "e", "a"), sections.triggered.map { it.alert.id })
        assertEquals(listOf("100.01", null, "100.02"), sections.triggered.map { it.observedPrice?.toPlainString() })
        assertEquals(listOf("c", "z"), sections.disabled.map { it.id })
    }
}
