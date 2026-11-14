package io.github.allisson.bitcoinalert.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.ui.DateFormats
import io.github.allisson.bitcoinalert.ui.home.formatPrice
import io.github.allisson.bitcoinalert.ui.rememberDateFormats
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun HistoryScreen() {
    val repository = (LocalContext.current.applicationContext as BitcoinAlertApp).alertRepository
    val history by repository.history.collectAsStateWithLifecycle(null)
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberDateFormats()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.history_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val entries = history ?: return@LazyColumn
        if (entries.isEmpty()) {
            item {
                Text(stringResource(R.string.no_history), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        groupByDay(entries, zone).forEach { (day, dayEntries) ->
            item(key = day.toString()) {
                Text(
                    dayLabel(day, today, dayEntries.first().triggeredAt, dates).uppercase(),
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dayEntries, key = { it.id }) { HistoryRow(it, locale, dates) }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate, epochMillis: Long, dates: DateFormats): String = when (day) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> dates.date(epochMillis)
}

@Composable
private fun HistoryRow(entry: AlertHistoryEntry, locale: Locale, dates: DateFormats) {
    val above = entry.direction == AlertDirection.ABOVE
    val color = if (above) LocalPriceColors.current.up else LocalPriceColors.current.down
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The direction is in the text next to it.
        Icon(painterResource(if (above) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down), null, Modifier.size(20.dp), tint = color)
        Column(Modifier.weight(1f)) {
            // A FlowRow moves the whole price to the next line at large font sizes instead of breaking it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(if (above) R.string.above else R.string.below),
                    Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                Text(
                    formatPrice(entry.targetPrice, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.observed), style = MaterialTheme.typography.bodySmall, color = muted)
                Text(formatPrice(entry.observedPrice, locale), style = MaterialTheme.typography.bodySmall, fontFamily = MonoFamily)
            }
        }
        Text(dates.time(entry.triggeredAt), style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily, color = muted)
    }
}
