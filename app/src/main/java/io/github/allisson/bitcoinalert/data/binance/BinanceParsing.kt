package io.github.allisson.bitcoinalert.data.binance

import android.util.Log
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

private const val TAG = "Binance"

private val json = Json { ignoreUnknownKeys = true }

/** Throws [ArithmeticException] for a price finer than the BTCUSDT tick size (0.01). */
internal fun parsePrice(text: String): BigDecimal = BigDecimal(text).setScale(2, RoundingMode.UNNECESSARY)

sealed interface StreamMessage {
    data class TickerUpdate(val lastPrice: BigDecimal, val changePercent: BigDecimal) : StreamMessage

    data class KlineUpdate(val stream: String, val timeframe: Timeframe, val candle: Candle) : StreamMessage

    /** Response to SUBSCRIBE/UNSUBSCRIBE; [id] is null when Binance could not read the request's. */
    data class ControlResponse(val id: Long?, val success: Boolean) : StreamMessage
}

@Serializable
private class TickerData(
    @SerialName("c") val lastPrice: String,
    @SerialName("P") val changePercent: String,
)

@Serializable
private class KlineData(@SerialName("k") val kline: Kline)

@Serializable
private class Kline(
    @SerialName("t") val openTime: Long,
    @SerialName("i") val interval: String,
    @SerialName("o") val open: String,
    @SerialName("h") val high: String,
    @SerialName("l") val low: String,
    @SerialName("c") val close: String,
    @SerialName("v") val volume: String,
)

/** Returns null, after logging, for a message to discard: malformed, unknown, or with an invalid price. */
fun parseStreamMessage(text: String): StreamMessage? {
    try {
        val root = json.parseToJsonElement(text).jsonObject
        val stream = root["stream"]?.jsonPrimitive?.content
        val data = root["data"]
        return when {
            // Control responses are not wrapped: {"result": null, "id": n} or {"code", "msg", "id"?}.
            stream == null && ("result" in root || "code" in root) -> StreamMessage.ControlResponse(
                id = root["id"]?.jsonPrimitive?.longOrNull,
                success = "code" !in root && root["result"] is JsonNull,
            )
            stream == null || data == null -> discard("unknown message", text)
            stream.endsWith("@ticker") -> {
                val ticker = json.decodeFromJsonElement(TickerData.serializer(), data)
                StreamMessage.TickerUpdate(parsePrice(ticker.lastPrice), BigDecimal(ticker.changePercent))
            }
            "@kline_" in stream -> {
                val k = json.decodeFromJsonElement(KlineData.serializer(), data).kline
                val timeframe = Timeframe.entries.firstOrNull { it.binanceInterval == k.interval }
                    ?: return discard("unknown interval", text)
                StreamMessage.KlineUpdate(stream, timeframe, candle(k.openTime, k.open, k.high, k.low, k.close, k.volume))
            }
            else -> discard("unknown stream", text)
        }
    } catch (e: IllegalArgumentException) { // includes SerializationException and NumberFormatException
        return discard("malformed message (${e.message})", text)
    } catch (e: ArithmeticException) {
        return discard("price finer than the tick size", text)
    }
}

/**
 * Parses the REST klines response (an array of arrays). Throws [SerializationException] for a
 * malformed body or a price finer than the tick size: the whole load fails (spec.md §3.3).
 */
fun parseKlines(text: String): List<Candle> = try {
    json.parseToJsonElement(text).jsonArray.map { element ->
        val row = element.jsonArray
        fun field(i: Int) = row[i].jsonPrimitive.content
        candle(row[0].jsonPrimitive.long, field(1), field(2), field(3), field(4), field(5))
    }
} catch (e: SerializationException) {
    throw e
} catch (e: IllegalArgumentException) {
    throw SerializationException("Malformed klines response", e)
} catch (e: IndexOutOfBoundsException) {
    throw SerializationException("Malformed klines response", e)
} catch (e: ArithmeticException) {
    throw SerializationException("Kline price finer than the tick size", e)
}

private fun candle(openTime: Long, open: String, high: String, low: String, close: String, volume: String) =
    Candle(openTime, parsePrice(open), parsePrice(high), parsePrice(low), parsePrice(close), BigDecimal(volume))

private fun discard(reason: String, text: String): StreamMessage? {
    Log.w(TAG, "Discarding $reason: $text")
    return null
}
