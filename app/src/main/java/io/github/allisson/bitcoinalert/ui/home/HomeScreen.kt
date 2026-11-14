package io.github.allisson.bitcoinalert.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.data.repository.CandleState
import io.github.allisson.bitcoinalert.domain.model.ConnectionState
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.ui.charts.CandleChart
import io.github.allisson.bitcoinalert.ui.rememberNotificationPermissionRequest
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    // This destination's lifecycle is STARTED only while Home is the current route and the app is visible.
    val homeVisible = (LocalContext.current.applicationContext as BitcoinAlertApp).homeVisible
    LifecycleStartEffect(homeVisible) {
        homeVisible.value = true
        onStopOrDispose { homeVisible.value = false }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val requestNotifications = rememberNotificationPermissionRequest()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PriceSection(state, locale)
        monitoring?.let {
            MonitoringRow(it, onStart = { requestNotifications(); viewModel.startMonitoring() }, onStop = viewModel::stopMonitoring)
        }
        TimeframeTabs(state.timeframe, viewModel::selectTimeframe)
        CandlesArea(state.candles, locale, viewModel::retryCandles)
    }
}

@Composable
private fun PriceSection(state: HomeUiState, locale: Locale) {
    val placeholder = stringResource(R.string.price_placeholder)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pair), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ConnectionPill(state.connection)
            if (state.stale) StalePill()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                state.ticker?.let { formatPrice(it.lastPrice, locale) } ?: placeholder,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.ticker != null) {
                Text(stringResource(R.string.usdt), Modifier.alignByBaseline(), color = muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        val change = state.ticker?.let { formatChange(it.changePercent, locale) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                change?.text ?: placeholder,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = when (change?.direction) {
                    ChangeDirection.Up -> LocalPriceColors.current.up
                    ChangeDirection.Down -> LocalPriceColors.current.down
                    else -> muted
                },
            )
            Text(stringResource(R.string.change_period), Modifier.alignByBaseline(), color = muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MonitoringRow(monitoring: MonitoringUi, onStart: () -> Unit, onStop: () -> Unit) {
    val (title, dot) = when (monitoring.state) {
        MonitoringState.Running -> R.string.monitoring_running to LocalPriceColors.current.up
        MonitoringState.Stopped -> R.string.monitoring_stopped to MaterialTheme.colorScheme.onSurfaceVariant
        MonitoringState.Interrupted -> R.string.monitoring_interrupted to MaterialTheme.colorScheme.tertiary
    }
    // The buttons move to the next line as a whole at large font sizes instead of squeezing the title.
    FlowRow(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (monitoring.activeAlerts == 0) {
                        stringResource(R.string.no_active_alerts)
                    } else {
                        pluralStringResource(R.plurals.active_alerts, monitoring.activeAlerts, monitoring.activeAlerts)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (monitoring.state.canStop) OutlinedButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
            if (monitoring.state.canStart) Button(onClick = onStart) { Text(stringResource(R.string.start)) }
        }
    }
}

@Composable
private fun ConnectionPill(connection: ConnectionState) {
    val (label, dot) = when (connection) {
        ConnectionState.Connected -> R.string.connected to LocalPriceColors.current.up
        ConnectionState.Reconnecting -> R.string.reconnecting to MaterialTheme.colorScheme.tertiary
        else -> R.string.connecting to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StalePill() {
    val description = stringResource(R.string.stale)
    Pill(MaterialTheme.colorScheme.tertiaryContainer, Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(stringResource(R.string.stale_short), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun Pill(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.background(color, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun TimeframeTabs(selected: Timeframe?, onSelect: (Timeframe) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Timeframe.entries.forEach { timeframe ->
            val on = timeframe == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(timeframe) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    timeframe.binanceInterval,
                    fontFamily = MonoFamily,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CandlesArea(candles: CandleState, locale: Locale, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (candles) {
            CandleState.Loading -> CircularProgressIndicator()
            is CandleState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.candles_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            // Every load passes through Loading, so each load gets a new chart that opens at the last candle.
            is CandleState.Loaded -> if (candles.candles.isNotEmpty()) {
                CandleChart(candles.timeframe, candles.candles, locale)
            }
        }
    }
}
