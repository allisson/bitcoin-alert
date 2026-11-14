package io.github.allisson.bitcoinalert.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.candlestickModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.Candle
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import io.github.allisson.bitcoinalert.ui.home.formatPrice
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.math.BigDecimal
import java.time.ZoneId
import java.util.Locale

private val CANDLE_WIDTH = 8.dp
private val CANDLE_SPACING = 4.dp
private val LABEL_GAP = 2.dp

/** The `openTime` of every rising candle, so the volume bar takes its candle's color. */
private val RisingKey = ExtraStore.Key<Set<Double>>()

/**
 * Candles on the start axis and volume bars on the end axis of one chart, so they share scroll
 * and zoom (spec.md §2.3), with the last price line and a line per Active alert. Tapping a candle
 * selects it; the lines above show the selected candle, or the last one when none is selected.
 * A long press on the price area passes the alert for that price to [onLongPress].
 * [candles] must not be empty; [price] is the current ticker price, for the alerts' distance.
 */
@Composable
fun CandleChart(
    timeframe: Timeframe,
    candles: List<Candle>,
    alerts: List<PriceAlert>,
    price: BigDecimal?,
    locale: Locale,
    onLongPress: (AlertDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            extras { it[RisingKey] = candles.filter { c -> c.close >= c.open }.map { c -> c.openTime.toDouble() }.toSet() }
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
    val priceColors = LocalPriceColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val change = candleChange(shown)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatCandleTime(shown.openTime, timeframe, ZoneId.systemDefault(), locale),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatPercent(change, locale, signed = true) + "%",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = when (change.signum()) {
                        1 -> priceColors.up
                        -1 -> priceColors.down
                        else -> muted
                    },
                )
            }
            Text(
                stringResource(
                    R.string.candle_ohlc,
                    formatPrice(shown.open, locale),
                    formatPrice(shown.high, locale),
                    formatPrice(shown.low, locale),
                    formatPrice(shown.close, locale),
                    formatVolume(shown.volume, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonoFamily,
                color = muted,
            )
        }
        ProvideVicoTheme(rememberM3VicoTheme()) {
            val zone = remember { ZoneId.systemDefault() }
            // Tag text on the rising, falling and text colors: the surface color keeps a 4.5:1 contrast.
            val onTag = MaterialTheme.colorScheme.surface
            val upTag = tag(priceColors.up, onTag)
            val downTag = tag(priceColors.down, onTag)
            val textTag = tag(MaterialTheme.colorScheme.onSurface, onTag)
            val priceLines = remember(locale, priceColors, upTag, downTag) {
                PriceLines(
                    upLine = LineComponent(Fill(priceColors.up), 1.dp, DashedShape(RectangleShape, 6.dp, 4.dp)),
                    downLine = LineComponent(Fill(priceColors.down), 1.dp, DashedShape(RectangleShape, 6.dp, 4.dp)),
                    lastUpLine = LineComponent(Fill(priceColors.up), 1.dp, DashedShape(RectangleShape, 3.dp, 3.dp)),
                    lastDownLine = LineComponent(Fill(priceColors.down), 1.dp, DashedShape(RectangleShape, 3.dp, 3.dp)),
                    upTag = upTag,
                    downTag = downTag,
                    locale = locale,
                )
            }
            SideEffect {
                priceLines.last = candles.last()
                priceLines.alerts = alerts
                priceLines.price = price
            }
            val axisLabel = rememberAxisLabelComponent(
                style = TextStyle(color = muted, fontSize = 10.sp, fontFamily = MonoFamily),
            )
            val crosshair = rememberLineComponent(Fill(muted), 1.dp, DashedShape(RectangleShape, 2.dp, 3.dp))
            val marker = remember(crosshair, textTag, timeframe, locale) {
                CrosshairMarker(
                    crosshair,
                    textTag,
                    formatPrice = { y -> formatAxisPrice(y, locale) },
                    formatTime = { x -> formatCandleTime(x.toLong(), timeframe, zone, locale) },
                )
            }
            val haptics = LocalHapticFeedback.current
            // Before the first ticker, the last close stands in for the current price.
            val reference by rememberUpdatedState(price ?: candles.last().close)
            val onDraft by rememberUpdatedState(onLongPress)
            val tapToggle = CartesianMarkerController.rememberToggleOnTap()
            val markerController = remember(tapToggle, priceLines) {
                TapOrLongPress(tapToggle) { y ->
                    priceLines.draftAt(y, reference)?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraft(it)
                    }
                }
            }
            val chart = rememberCartesianChart(
                rememberCandlestickCartesianLayer(
                    candleProvider = CandlestickCartesianLayer.CandleProvider.absolute(
                        bullish = candle(priceColors.up),
                        neutral = candle(muted),
                        bearish = candle(priceColors.down),
                    ),
                    rangeProvider = CartesianLayerRangeProvider.Intrinsic,
                    verticalAxisPosition = Axis.Position.Vertical.Start,
                ),
                rememberColumnCartesianLayer(
                    columnProvider = VolumeColumns(
                        rising = rememberLineComponent(Fill(priceColors.up.copy(alpha = 0.3f)), CANDLE_WIDTH),
                        falling = rememberLineComponent(Fill(priceColors.down.copy(alpha = 0.3f)), CANDLE_WIDTH),
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
                marker = marker,
                markerVisibilityListener = markerListener,
                decorations = listOf(priceLines),
                markerController = markerController,
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

/** A rounded price tag: [text] on [color]. */
@Composable
private fun tag(color: Color, text: Color) = rememberTextComponent(
    style = TextStyle(color = text, fontSize = 10.sp, fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold),
    padding = Insets(6.dp, 2.dp),
    background = rememberShapeComponent(Fill(color), RoundedCornerShape(4.dp)),
)

/** Zoom that shows [count] candles across the chart width. */
private fun visibleCandles(count: Int) = Zoom { _, layerDimensions, bounds -> bounds.width / (count * layerDimensions.xSpacing) }

/** Volume bars use the bottom quarter of the chart: 0 at the bottom, the highest bar at a quarter. */
private object VolumeRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) = maxY * 4
}

/** Each volume bar in its candle's rising or falling color. */
private class VolumeColumns(private val rising: LineComponent, private val falling: LineComponent) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore) =
        if (extraStore.getOrNull(RisingKey)?.contains(entry.x) != false) rising else falling

    override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore) = rising
}

/** Follows a new candle only when the chart showed the last candle before the update. */
private class StickToEnd : AutoScrollCondition {
    var atEnd = true

    override fun shouldScroll(oldModel: CartesianChartModel?, newModel: CartesianChartModel) =
        atEnd && oldModel != null && newModel.width > oldModel.width
}

/** Canvas y of a price on the candles' (start) axis. */
private fun CartesianDrawingContext.priceY(value: Double): Float {
    val range = ranges.getYRange(Axis.Position.Vertical.Start)
    return (layerBounds.bottom - (value - range.minY) / range.length * layerBounds.height).toFloat()
}

/**
 * The last price as a dashed line with a tag at the end, in the last candle's color, and each
 * Active alert as a dashed line with its label at the start; an alert outside the candles' range
 * is pinned to that edge, without a line (spec.md §2.3). The state fields are read while drawing,
 * so a change redraws the chart.
 */
private class PriceLines(
    private val upLine: LineComponent,
    private val downLine: LineComponent,
    private val lastUpLine: LineComponent,
    private val lastDownLine: LineComponent,
    private val upTag: TextComponent,
    private val downTag: TextComponent,
    private val locale: Locale,
) : Decoration {
    var last by mutableStateOf<Candle?>(null)
    var alerts by mutableStateOf<List<PriceAlert>>(emptyList())
    var price by mutableStateOf<BigDecimal?>(null)

    // The price axis as last drawn, to turn a long press into a price; not state, so no redraw.
    private var top = 0f
    private var bottom = 0f
    private var minY = 0.0
    private var maxY = 0.0

    /** `null` before the first draw and outside the price area. */
    fun draftAt(y: Float, price: BigDecimal) = alertDraftAt(y, top, bottom, minY, maxY, price)

    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val range = context.ranges.getYRange(Axis.Position.Vertical.Start)
        top = bounds.top
        bottom = bounds.bottom
        minY = range.minY
        maxY = range.maxY
        // Highest target first, so labels at the same edge stack in price order.
        val shown = alerts.sortedByDescending { it.targetPrice }.map { alert ->
            val pin = pinOf(alert.targetPrice.toDouble(), range.minY, range.maxY)
            Triple(alert, pin, alertLabel(alert.direction, alert.targetPrice, price, pin, locale))
        }
        if (shown.isNotEmpty()) {
            val height = upTag.getHeight(context, shown.first().third)
            val lineYs = shown.map { (alert, pin) ->
                when (pin) {
                    Pin.Top -> bounds.top + height / 2
                    Pin.Bottom -> bounds.bottom - height / 2
                    null -> context.priceY(alert.targetPrice.toDouble())
                }
            }
            val labelYs = stackLabels(lineYs, height, with(context) { LABEL_GAP.pixels }, bounds.top, bounds.bottom)
            shown.forEachIndexed { i, (alert, pin, label) ->
                val above = alert.direction == AlertDirection.ABOVE
                if (pin == null) (if (above) upLine else downLine).drawHorizontal(context, bounds.left, bounds.right, lineYs[i])
                val tag = if (above) upTag else downTag
                tag.draw(context, label, bounds.left + tag.getWidth(context, label) / 2, labelYs[i], Position.Horizontal.Center, Position.Vertical.Center)
            }
        }
        val candle = last ?: return
        val rising = candle.close >= candle.open
        val y = context.priceY(candle.close.toDouble())
        (if (rising) lastUpLine else lastDownLine).drawHorizontal(context, bounds.left, bounds.right, y)
        val tag = if (rising) upTag else downTag
        val label = formatAxisPrice(candle.close.toDouble(), locale)
        tag.draw(context, label, bounds.right - tag.getWidth(context, label) / 2, y, Position.Horizontal.Center, Position.Vertical.Center)
    }
}

/** Vico's toggle-on-tap marker, plus a long press that reports its canvas y and leaves the marker as it is. */
private class TapOrLongPress(
    private val tap: CartesianMarkerController,
    private val onLongPress: (Float) -> Unit,
) : CartesianMarkerController by tap {
    override val acceptsLongPress = true

    override fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean {
        if (interaction is Interaction.LongPress) {
            onLongPress(interaction.point.y)
            return false
        }
        return tap.shouldAcceptInteraction(interaction, targets)
    }
}

/**
 * Crosshair on the selected candle: a vertical line, a horizontal line at its close, the close
 * tagged at the end of the chart and the time tagged below it, over the time axis.
 */
private class CrosshairMarker(
    private val line: LineComponent,
    private val tag: TextComponent,
    private val formatPrice: (Double) -> String,
    private val formatTime: (Double) -> String,
) : CartesianMarker {

    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.filterIsInstance<CandlestickCartesianLayerMarkerTarget>().firstOrNull() ?: return
        val bounds = context.layerBounds
        line.drawVertical(context, target.canvasX, bounds.top, bounds.bottom)
        val y = target.closingCanvasY
        line.drawHorizontal(context, bounds.left, bounds.right, y)
        val price = formatPrice(target.entry.closing)
        tag.draw(context, price, bounds.right - tag.getWidth(context, price) / 2, y, Position.Horizontal.Center, Position.Vertical.Center)
        val time = formatTime(target.x)
        val half = tag.getWidth(context, time) / 2
        val x = target.canvasX.coerceIn(bounds.left + half, bounds.right - half)
        tag.draw(context, time, x, bounds.bottom + tag.getHeight(context, time) / 2 + 2f, Position.Horizontal.Center, Position.Vertical.Center)
    }
}
