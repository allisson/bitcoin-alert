package io.github.allisson.bitcoinalert.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import io.github.allisson.bitcoinalert.ui.DateFormats
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import io.github.allisson.bitcoinalert.ui.home.formatPrice
import io.github.allisson.bitcoinalert.ui.rememberDateFormats
import io.github.allisson.bitcoinalert.ui.rememberNotificationPermissionRequest
import io.github.allisson.bitcoinalert.ui.theme.LocalPriceColors
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.math.BigDecimal
import java.util.Locale

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory)) {
    // While Alerts is visible the connection carries the ticker, for the distance to each target (spec.md §3.3).
    val alertsVisible = (LocalContext.current.applicationContext as BitcoinAlertApp).alertsVisible
    LifecycleStartEffect(alertsVisible) {
        alertsVisible.value = true
        onStopOrDispose { alertsVisible.value = false }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val dates = rememberDateFormats()
    val requestNotifications = rememberNotificationPermissionRequest()
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
            item { Header(state.price, locale) }
            val sections = state.sections
            if (sections != null) {
                if (sections.active.isEmpty() && sections.triggered.isEmpty() && sections.disabled.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_alerts),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                section(R.string.state_active, sections.active) { alert ->
                    AlertRow(alert, locale, subtitle = activeSubtitle(alert, state.price, locale, dates), onDelete = { deleting = alert.id },
                        bar = state.price?.let { distance(alert.direction, alert.targetPrice, it) as? Distance.Away }?.fill,
                    ) { Switch(true, { viewModel.setEnabled(alert.id, false) }, Modifier.switchLabel()) }
                }
                section(R.string.state_triggered, sections.triggered) { (alert, observed) ->
                    val at = dates.dateTime(alert.triggeredAt!!)
                    val subtitle = observed?.let { stringResource(R.string.hit, formatPrice(it, locale), at) }
                        ?: stringResource(R.string.triggered_at, at)
                    AlertRow(alert, locale, subtitle, onDelete = { deleting = alert.id }) {
                        OutlinedButton({ viewModel.reactivate(alert.id) }) { Text(stringResource(R.string.reactivate)) }
                    }
                }
                section(R.string.state_disabled, sections.disabled) { alert ->
                    AlertRow(alert, locale, stringResource(R.string.off_created, dates.date(alert.createdAt)), onDelete = { deleting = alert.id }) {
                        Switch(false, { viewModel.setEnabled(alert.id, true) }, Modifier.switchLabel())
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.new_alert)) },
        )
    }

    if (creating) {
        NewAlertSheet(state.price, locale, onDismiss = { creating = false }) { direction, target ->
            requestNotifications()
            viewModel.create(direction, target)
            creating = false
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton({
                    viewModel.delete(id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(price: BigDecimal?, locale: Locale) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.alerts), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (price != null) {
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(stringResource(R.string.current_price), Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium, color = muted)
                Text(formatPrice(price, locale), Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium, fontFamily = MonoFamily, color = muted)
            }
        }
    }
}

private fun <T> LazyListScope.section(title: Int, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item {
        Text(
            stringResource(R.string.section, stringResource(title), rows.size).uppercase(),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(rows) { row(it) }
}

@Composable
private fun activeSubtitle(alert: PriceAlert, price: BigDecimal?, locale: Locale, dates: DateFormats): String {
    val created = dates.date(alert.createdAt)
    return when (val distance = price?.let { distance(alert.direction, alert.targetPrice, it) }) {
        null -> stringResource(R.string.created_capitalized, created)
        is Distance.Away -> stringResource(R.string.away_created, formatPercent(distance.percent, locale), created)
        Distance.Beyond -> stringResource(if (alert.direction == AlertDirection.ABOVE) R.string.beyond_above else R.string.beyond_below)
    }
}

@Composable
private fun Modifier.switchLabel(): Modifier {
    val label = stringResource(R.string.alert_enabled)
    return semantics { contentDescription = label }
}

@Composable
private fun AlertRow(
    alert: PriceAlert,
    locale: Locale,
    subtitle: String,
    onDelete: () -> Unit,
    bar: Float? = null,
    trailing: @Composable () -> Unit,
) {
    val above = alert.direction == AlertDirection.ABOVE
    val color = if (above) LocalPriceColors.current.up else LocalPriceColors.current.down
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The direction is in the text next to it.
        Icon(painterResource(if (above) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down), null, Modifier.size(20.dp), tint = color)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (above) R.string.above else R.string.below),
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                Text(
                    formatPrice(alert.targetPrice, locale),
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
            if (bar != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { bar },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        trailing()
        AlertMenu(onDelete)
    }
}

@Composable
private fun AlertMenu(onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                open = false
                onDelete()
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewAlertSheet(
    price: BigDecimal?,
    locale: Locale,
    onDismiss: () -> Unit,
    onCreate: (AlertDirection, BigDecimal) -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf(AlertDirection.ABOVE) }
    var text by rememberSaveable { mutableStateOf("") }
    val target = parseTargetPrice(text)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.new_alert), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AlertDirection.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = direction == option,
                        onClick = { direction = option },
                        shape = SegmentedButtonDefaults.itemShape(index, AlertDirection.entries.size),
                        icon = {
                            val up = option == AlertDirection.ABOVE
                            Icon(painterResource(if (up) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down), null, Modifier.size(18.dp))
                        },
                    ) { Text(stringResource(if (option == AlertDirection.ABOVE) R.string.above else R.string.below)) }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.target_price)) },
                suffix = { Text(stringResource(R.string.usdt)) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = MonoFamily),
                isError = text.isNotBlank() && target == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                supportingText = {
                    when {
                        target != null -> {
                            val saves = formatPrice(target, locale)
                            Text(
                                price?.let { stringResource(R.string.saves_as_from_now, saves, formatPercent(percentFromPrice(target, it), locale, signed = true)) }
                                    ?: stringResource(R.string.saves_as, saves),
                            )
                        }
                        text.isNotBlank() -> Text(stringResource(R.string.target_invalid))
                    }
                },
            )
            if (target != null && price != null && distance(direction, target, price) == Distance.Beyond) {
                Text(
                    stringResource(if (direction == AlertDirection.ABOVE) R.string.already_above else R.string.already_below),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Button(onClick = { target?.let { onCreate(direction, it) } }, enabled = target != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.create_alert))
            }
        }
    }
}
