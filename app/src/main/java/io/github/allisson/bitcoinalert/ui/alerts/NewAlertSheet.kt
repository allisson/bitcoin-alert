package io.github.allisson.bitcoinalert.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.ui.home.formatPercent
import io.github.allisson.bitcoinalert.ui.home.formatPrice
import io.github.allisson.bitcoinalert.ui.theme.MonoFamily
import java.math.BigDecimal
import java.util.Locale

/** The New alert form, from the Alerts FAB or, filled in, from a long press on the chart (spec.md §2.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlertSheet(
    price: BigDecimal?,
    locale: Locale,
    onDismiss: () -> Unit,
    initialDirection: AlertDirection = AlertDirection.ABOVE,
    initialTarget: BigDecimal? = null,
    onCreate: (AlertDirection, BigDecimal) -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf(initialDirection) }
    var text by rememberSaveable { mutableStateOf(initialTarget?.stripTrailingZeros()?.toPlainString().orEmpty()) }
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
