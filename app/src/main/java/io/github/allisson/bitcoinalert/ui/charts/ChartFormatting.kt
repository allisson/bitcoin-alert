package io.github.allisson.bitcoinalert.ui.charts

import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Candle volume in BTC with the locale's grouping and trailing zeros removed (spec.md §2.1). */
fun formatVolume(volume: BigDecimal, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = volume.scale() }.format(volume)

/** Price axis label: the locale's grouping, no decimal places. */
fun formatAxisPrice(value: Double, locale: Locale): String = NumberFormat.getIntegerInstance(locale).apply {
    roundingMode = RoundingMode.HALF_UP
}.format(value)

/** Time axis label: `HH:mm` for intraday candles, the short date for `1d`. */
fun formatAxisTime(openTime: Long, timeframe: Timeframe, zone: ZoneId, locale: Locale): String =
    format(openTime, zone, if (timeframe == Timeframe.D1) shortDate(locale) else DateTimeFormatter.ofPattern("HH:mm", locale))

/** The selected candle's open time: short date and time for intraday candles, the short date for `1d`. */
fun formatCandleTime(openTime: Long, timeframe: Timeframe, zone: ZoneId, locale: Locale): String =
    format(
        openTime,
        zone,
        if (timeframe == Timeframe.D1) shortDate(locale) else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale),
    )

private fun shortDate(locale: Locale) = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)

private fun format(epochMillis: Long, zone: ZoneId, formatter: DateTimeFormatter) =
    formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
