package io.github.allisson.bitcoinalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.allisson.bitcoinalert.data.binance.desiredStreams
import io.github.allisson.bitcoinalert.data.repository.CandleState
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.ui.theme.BitcoinAlertTheme

/** shortcut: plain-text market data screen to validate Phase 1 against Binance; Phase 2 replaces it with Home. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BitcoinAlertApp
        val streamClient = app.binanceStreamClient
        val candleRepository = app.candleRepository
        setContent {
            LifecycleStartEffect(Unit) {
                streamClient.setDesiredStreams(desiredStreams(homeVisible = true, monitoringRunning = false, Timeframe.H1))
                onStopOrDispose {
                    streamClient.setDesiredStreams(desiredStreams(homeVisible = false, monitoringRunning = false, Timeframe.H1))
                }
            }
            val connection by streamClient.connectionState.collectAsStateWithLifecycle()
            val ticker by streamClient.ticker.collectAsStateWithLifecycle()
            val candles by candleRepository.state.collectAsStateWithLifecycle()
            BitcoinAlertTheme {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        Text("Connection: $connection")
                        Text("BTC/USDT: ${ticker?.lastPrice?.toPlainString() ?: "—"}")
                        Text("24h change: ${ticker?.changePercent?.toPlainString() ?: "—"}%")
                        Text(
                            when (val state = candles) {
                                CandleState.Loading -> "Candles: loading"
                                is CandleState.Error -> "Candles ${state.timeframe}: error"
                                is CandleState.Loaded -> "Candles ${state.timeframe}: ${state.candles.size}, last " +
                                    state.candles.lastOrNull()?.let { "open ${it.openTime} close ${it.close} vol ${it.volume}" }
                            },
                        )
                    }
                }
            }
        }
    }
}
