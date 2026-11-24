package io.github.allisson.bitcoinalert.ui.alerts

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.allisson.bitcoinalert.R

/** Marks an alert, or a history entry, with the Alarm delivery (spec.md §4). */
@Composable
fun AlarmIcon(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_alarm),
        contentDescription = stringResource(R.string.alarm),
        modifier = modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
