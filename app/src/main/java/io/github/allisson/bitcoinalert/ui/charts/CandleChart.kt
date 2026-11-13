package io.github.allisson.bitcoinalert.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.candlestickModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.ui.home.formatPrice
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.time.ZoneId
import java.util.Locale

private val CANDLE_WIDTH = 8.dp
private val CANDLE_SPACING = 4.dp

/**
 * Candles on the start axis and volume bars on the end axis of one chart, so they share scroll
 * and zoom (spec.md §2.3). Tapping a candle selects it; the line above shows the selected candle,
 * or the last one when none is selected. [candles] must not be empty.
 */
@Composable
fun CandleChart(timeframe: Timeframe, candles: List<Candle>, locale: Locale, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    val stickToEnd = remember { StickToEnd() }
    val scrollState = rememberVicoScrollState(
        initialScroll = Scroll.Absolute.End,
        autoScroll = Scroll.Absolute.End,
        autoScrollCondition = stickToEnd,
    )
    val zoomState = rememberVicoZoomState(
        initialZoom = remember { Zoom.max(visibleCandles(60), Zoom.Content) },
        minZoom = Zoom.Content,
        maxZoom = remember { Zoom.max(visibleCandles(15), Zoom.Content) },
    )
    LaunchedEffect(candles) {
        // Read before the transaction: Vico updates the scroll range before asking the condition.
        stickToEnd.atEnd = scrollState.value >= scrollState.maxValue - 1f
        val x = candles.map { it.openTime }
        producer.runTransaction {
            candlestickModel(x, candles.map { it.open }, candles.map { it.close }, candles.map { it.low }, candles.map { it.high })
            columnModel { series(x, candles.map { it.volume }) }
        }
    }

    var selectedX by remember { mutableStateOf<Double?>(null) }
    val markerListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                selectedX = targets.firstOrNull()?.x
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                selectedX = targets.firstOrNull()?.x
            }

            override fun onHidden(marker: CartesianMarker) {
                selectedX = null
            }
        }
    }
    val shown = candles.lastOrNull { it.openTime.toDouble() == selectedX } ?: candles.last()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.candle_ohlc,
                formatCandleTime(shown.openTime, timeframe, ZoneId.systemDefault(), locale),
                formatPrice(shown.open, locale),
                formatPrice(shown.high, locale),
                formatPrice(shown.low, locale),
                formatPrice(shown.close, locale),
                formatVolume(shown.volume, locale),
            ),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProvideVicoTheme(rememberM3VicoTheme()) {
            val priceColors = LocalPriceColors.current
            val zone = remember { ZoneId.systemDefault() }
            val guideline = rememberLineComponent(Fill(MaterialTheme.colorScheme.onSurfaceVariant), 1.dp)
            val axisLabel = rememberAxisLabelComponent(
                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = MonoFamily),
            )
            val chart = rememberCartesianChart(
                rememberCandlestickCartesianLayer(
                    candleProvider = CandlestickCartesianLayer.CandleProvider.absolute(
                        bullish = candle(priceColors.up),
                        neutral = candle(MaterialTheme.colorScheme.onSurfaceVariant),
                        bearish = candle(priceColors.down),
                    ),
                    rangeProvider = CartesianLayerRangeProvider.Intrinsic,
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(Fill(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)), CANDLE_WIDTH),
                    ),
                    columnCollectionSpacing = CANDLE_SPACING,
                    rangeProvider = VolumeRange,
                    verticalAxisPosition = Axis.Position.Vertical.End,
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = axisLabel,
                    valueFormatter = remember(locale) { CartesianValueFormatter { _, y, _ -> formatAxisPrice(y, locale) } },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = axisLabel,
                    valueFormatter = remember(timeframe, locale) {
                        CartesianValueFormatter { _, x, _ -> formatAxisTime(x.toLong(), timeframe, zone, locale) }
                    },
                ),
                marker = remember(guideline) { GuidelineMarker(guideline) },
                markerVisibilityListener = markerListener,
                markerController = CartesianMarkerController.rememberToggleOnTap(),
            )
            CartesianChartHost(
                chart,
                producer,
                Modifier.fillMaxWidth().height(320.dp),
                scrollState = scrollState,
                zoomState = zoomState,
                animationSpec = null,
                initialAnimationSpec = null,
            )
        }
    }
}

@Composable
private fun candle(color: Color) = CandlestickCartesianLayer.Candle(rememberLineComponent(Fill(color), CANDLE_WIDTH))

/** Zoom that shows [count] candles across the chart width. */
private fun visibleCandles(count: Int) = Zoom { _, layerDimensions, bounds -> bounds.width / (count * layerDimensions.xSpacing) }

/** Volume bars use the bottom quarter of the chart: 0 at the bottom, the highest bar at a quarter. */
private object VolumeRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = maxY * 4
}

/** Follows a new candle only when the chart showed the last candle before the update. */
private class StickToEnd : AutoScrollCondition {
    var atEnd = true

    override fun shouldScroll(oldModel: CartesianChartModel?, newModel: CartesianChartModel) =
        atEnd && oldModel != null && newModel.width > oldModel.width
}

/** Marks the selected candle with a vertical line. */
private class GuidelineMarker(private val line: LineComponent) : CartesianMarker {
    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.firstOrNull() ?: return
        line.drawVertical(context, target.canvasX, context.layerBounds.top, context.layerBounds.bottom)
    }
}
