package io.github.allisson.bitcoinalert.ui.history

import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Groups [entries] (newest first) by their day in [zone], keeping the order inside each day. */
fun groupByDay(entries: List<AlertHistoryEntry>, zone: ZoneId): List<Pair<LocalDate, List<AlertHistoryEntry>>> =
    entries.groupBy { Instant.ofEpochMilli(it.triggeredAt).atZone(zone).toLocalDate() }.toList()
