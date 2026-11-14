package io.github.allisson.bitcoinalert.domain.alerts

import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.BELOW
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private var engine = AlertEngine()

    private fun alert(
        direction: AlertDirection,
        target: String,
        id: String = "a",
        cycle: Int = 0,
        enabled: Boolean = true,
        triggeredAt: Long? = null,
    ) = PriceAlert(id, BigDecimal(target), direction, enabled, triggeredAt, createdAt = 0, activationCycle = cycle)

    /** Feeds the prices in order and returns the ids triggered on each one. */
    private fun feed(alerts: List<PriceAlert>, vararg prices: String) =
        prices.map { price -> engine.onPrice(BigDecimal(price), alerts).map { it.id } }

    @Test
    fun `ABOVE triggers when the price goes from at or below the target to above it`() {
        val above = listOf(alert(ABOVE, "100.00"))
        assertEquals(listOf(listOf(), listOf("a")), feed(above, "99.99", "100.01"))
        engine = AlertEngine()
        assertEquals(listOf(listOf(), listOf("a")), feed(above, "100.00", "100.01"))
    }

    @Test
    fun `ABOVE does not trigger on reaching the target or staying above it`() {
        assertEquals(listOf(listOf<String>(), listOf(), listOf()), feed(listOf(alert(ABOVE, "100.00")), "99.99", "100.00", "100.00"))
        engine = AlertEngine()
        assertEquals(listOf(listOf<String>(), listOf()), feed(listOf(alert(ABOVE, "100.00")), "100.01", "100.02"))
    }

    @Test
    fun `BELOW triggers when the price goes from at or above the target to below it`() {
        val below = listOf(alert(BELOW, "100.00"))
        assertEquals(listOf(listOf(), listOf("a")), feed(below, "100.01", "99.99"))
        engine = AlertEngine()
        assertEquals(listOf(listOf(), listOf("a")), feed(below, "100.00", "99.99"))
    }

    @Test
    fun `BELOW does not trigger on reaching the target or staying below it`() {
        assertEquals(listOf(listOf<String>(), listOf(), listOf()), feed(listOf(alert(BELOW, "100.00")), "100.01", "100.00", "100.00"))
        engine = AlertEngine()
        assertEquals(listOf(listOf<String>(), listOf()), feed(listOf(alert(BELOW, "100.00")), "99.99", "99.98"))
    }

    @Test
    fun `comparisons ignore the scale`() {
        val above = listOf(alert(ABOVE, "100000.50"))
        assertEquals(listOf(listOf(), listOf("a")), feed(above, "100000.5", "100000.51000000"))
    }

    @Test
    fun `a new alert takes the first price as its baseline even when it is beyond the target`() {
        val above = listOf(alert(ABOVE, "100.00"))
        assertEquals(listOf(listOf<String>(), listOf()), feed(above, "150.00", "151.00"))
    }

    @Test
    fun `an alert created after a price takes the next price as its baseline`() {
        engine.onPrice(BigDecimal("90.00"), emptyList())
        // Against the previous price 90.00 this would be a crossing; the new cycle only gets its baseline.
        assertEquals(listOf(listOf<String>()), feed(listOf(alert(ABOVE, "100.00")), "110.00"))
    }

    @Test
    fun `a disable and enable between two prices starts a new baseline`() {
        assertEquals(listOf(listOf<String>()), feed(listOf(alert(ABOVE, "100.00", cycle = 0)), "99.00"))
        // Cycle 1 replaced cycle 0 without the engine seeing the alert Disabled.
        assertEquals(listOf(listOf<String>(), listOf()), feed(listOf(alert(ABOVE, "100.00", cycle = 1)), "101.00", "102.00"))
    }

    @Test
    fun `a reactivated alert takes the next price as its baseline`() {
        val first = alert(ABOVE, "100.00", cycle = 0)
        assertEquals(listOf(listOf(), listOf("a")), feed(listOf(first), "99.00", "101.00"))
        feed(listOf(first.copy(triggeredAt = 1)), "99.00")
        val reactivated = alert(ABOVE, "100.00", cycle = 1)
        assertEquals(listOf(listOf(), listOf(), listOf("a")), feed(listOf(reactivated), "101.00", "99.00", "101.00"))
    }

    @Test
    fun `Disabled and Triggered alerts are not evaluated`() {
        val alerts = listOf(
            alert(ABOVE, "100.00", id = "off", enabled = false),
            alert(ABOVE, "100.00", id = "hit", triggeredAt = 5),
        )
        assertEquals(listOf(listOf<String>(), listOf()), feed(alerts, "99.00", "101.00"))
    }

    @Test
    fun `baselines of cycles that are no longer Active are dropped`() {
        val a = alert(ABOVE, "100.00", id = "a")
        val b = alert(BELOW, "50.00", id = "b")
        feed(listOf(a, b), "75.00")
        assertEquals(setOf("a" to 0, "b" to 0), engine.trackedCycles)

        feed(listOf(a.copy(triggeredAt = 1), b.copy(activationCycle = 1)), "76.00")
        assertEquals(setOf("b" to 1), engine.trackedCycles)

        feed(emptyList(), "77.00")
        assertTrue(engine.trackedCycles.isEmpty())
    }

    @Test
    fun `the previous price is kept across a reconnection`() {
        val above = listOf(alert(ABOVE, "100.00"))
        feed(above, "99.00")
        // The same engine across a reconnection: the crossing during the gap triggers on the first price after it.
        assertEquals(listOf(listOf("a")), feed(above, "105.00"))
    }

    @Test
    fun `alerts crossed by the same price all trigger`() {
        val alerts = listOf(alert(ABOVE, "100.00", id = "x"), alert(ABOVE, "100.50", id = "y"), alert(BELOW, "90.00", id = "z"))
        assertEquals(listOf(listOf(), listOf("x", "y")), feed(alerts, "99.00", "101.00"))
    }
}
