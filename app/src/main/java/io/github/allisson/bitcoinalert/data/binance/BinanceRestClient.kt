package io.github.allisson.bitcoinalert.data.binance

import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class BinanceHttpException(val code: Int) : IOException("Binance HTTP $code")

class BinanceRestClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.binance.com".toHttpUrl(),
) {
    /**
     * Loads the latest 200 candles. Throws [IOException] on network failure, [BinanceHttpException]
     * on a non-2xx response and [kotlinx.serialization.SerializationException] on an invalid body.
     * Cancelling the coroutine cancels the HTTP call.
     */
    suspend fun klines(timeframe: Timeframe): List<Candle> {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/v3/klines")
            .addQueryParameter("symbol", "BTCUSDT")
            .addQueryParameter("interval", timeframe.binanceInterval)
            .addQueryParameter("limit", "200")
            .build()
        val call = httpClient.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw BinanceHttpException(it.code)
                            parseKlines(it.body.string())
                        }
                    }
                    result.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
                }
            })
        }
    }
}
