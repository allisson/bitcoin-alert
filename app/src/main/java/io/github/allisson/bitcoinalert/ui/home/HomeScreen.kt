package io.github.allisson.bitcoinalert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.data.repository.CandleState
import io.github.allisson.bitcoinalert.domain.model.ConnectionState
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenAlerts: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    // This destination's lifecycle is STARTED only while Home is the current route and the app is visible.
    val homeVisible = (LocalContext.current.applicationContext as BitcoinAlertApp).homeVisible
    LifecycleStartEffect(homeVisible) {
        homeVisible.value = true
        onStopOrDispose { homeVisible.value = false }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenAlerts) { Text(stringResource(R.string.alerts)) }
                    TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.history)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PriceSection(state, locale)
            TimeframeSelector(state.timeframe, viewModel::selectTimeframe)
            CandlesArea(state.candles, locale, viewModel::retryCandles)
        }
    }
}

@Composable
private fun PriceSection(state: HomeUiState, locale: Locale) {
    val placeholder = stringResource(R.string.price_placeholder)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.pair), style = MaterialTheme.typography.titleMedium)
        Text(
            state.ticker?.let { formatPrice(it.lastPrice, locale) } ?: placeholder,
            style = MaterialTheme.typography.displaySmall,
        )
        val change = state.ticker?.let { formatChange(it.changePercent, locale) }
        Text(
            change?.text ?: placeholder,
            style = MaterialTheme.typography.titleMedium,
            color = when (change?.direction) {
                ChangeDirection.Up -> LocalPriceColors.current.up
                ChangeDirection.Down -> LocalPriceColors.current.down
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            stringResource(
                when (state.connection) {
                    ConnectionState.Connected -> R.string.connected
                    ConnectionState.Reconnecting -> R.string.reconnecting
                    else -> R.string.connecting
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.stale) {
            Text(
                stringResource(R.string.stale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TimeframeSelector(selected: Timeframe?, onSelect: (Timeframe) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Timeframe.entries.forEachIndexed { index, timeframe ->
            SegmentedButton(
                selected = timeframe == selected,
                onClick = { onSelect(timeframe) },
                shape = SegmentedButtonDefaults.itemShape(index, Timeframe.entries.size),
            ) { Text(timeframe.binanceInterval) }
        }
    }
}

/** shortcut: text summary of the loaded candles; Phase 2b replaces it with the chart. */
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
            is CandleState.Loaded -> Text(
                stringResource(
                    R.string.candles_summary,
                    candles.candles.size,
                    candles.candles.lastOrNull()?.let { formatPrice(it.close, locale) } ?: "",
                ),
            )
        }
    }
}
