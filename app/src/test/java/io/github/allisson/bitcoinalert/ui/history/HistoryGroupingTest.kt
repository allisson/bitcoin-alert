package io.github.allisson.bitcoinalert.ui.history

import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupingTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun entry(id: Long, at: String) = AlertHistoryEntry(
        id, "a", BigDecimal("1.00"), BigDecimal("1.01"), ABOVE,
        ZonedDateTime.parse(at).toInstant().toEpochMilli(),
    )

    @Test
    fun `entries are grouped by day in the device time zone, newest first`() {
        val entries = listOf(
            entry(4, "2026-11-13T23:30:00-03:00"),
            entry(3, "2026-11-13T00:10:00-03:00"),
            entry(2, "2026-11-12T23:59:00-03:00"), // 02:59 UTC on Nov 13: still Nov 12 in São Paulo
            entry(1, "2026-11-08T22:48:00-03:00"),
        )
        val groups = groupByDay(entries, saoPaulo)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 8)),
            groups.map { it.first },
        )
        assertEquals(listOf(listOf(4L, 3L), listOf(2L), listOf(1L)), groups.map { (_, day) -> day.map { it.id } })
    }

    @Test
    fun `the same instants fall on other days in UTC`() {
        val groups = groupByDay(listOf(entry(4, "2026-11-13T23:30:00-03:00"), entry(3, "2026-11-13T00:10:00-03:00")), ZoneId.of("UTC"))
        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 13)), groups.map { it.first })
    }
}
