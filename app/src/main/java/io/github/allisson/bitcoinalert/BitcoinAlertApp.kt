package io.github.allisson.bitcoinalert

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import io.github.allisson.bitcoinalert.data.binance.BinanceRestClient
import io.github.allisson.bitcoinalert.data.binance.BinanceStreamClient
import io.github.allisson.bitcoinalert.data.binance.OkHttpWebSocketConnector
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Process-scoped instances (manual dependency injection). */
class BitcoinAlertApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient by lazy { OkHttpClient() }

    val binanceRestClient by lazy { BinanceRestClient(httpClient) }

    val binanceStreamClient by lazy {
        BinanceStreamClient(OkHttpWebSocketConnector(httpClient), appScope, SystemClock::elapsedRealtime, Random.Default)
            .also { client ->
                getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = client.onNetworkAvailable()
                    },
                )
            }
    }
}
