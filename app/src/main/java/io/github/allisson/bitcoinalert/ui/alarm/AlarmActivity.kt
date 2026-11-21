package io.github.allisson.bitcoinalert.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.notifications.alertText
import io.github.allisson.bitcoinalert.notifications.alertTitle
import io.github.allisson.bitcoinalert.ui.theme.BitcoinAlertTheme

/**
 * The alarm screen over the lock screen (spec.md §2.6, Alarm delivery). Its own task, separate from
 * [io.github.allisson.bitcoinalert.MainActivity], so it never exposes the rest of the application
 * while the device is locked. It closes once nothing rings.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val alarm = (application as BitcoinAlertApp).alarmController
        enableEdgeToEdge()
        setContent {
            BitcoinAlertTheme {
                val ringing by alarm.ringing.collectAsStateWithLifecycle()
                val latest = ringing.lastOrNull()
                LaunchedEffect(latest) { if (latest == null) finish() }
                if (latest != null) AlarmScreen(latest, ringing.size, alarm::dismiss)
            }
        }
    }
}

@Composable
private fun AlarmScreen(latest: AlertHistoryEntry, count: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(alertTitle(context, latest), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(alertText(context, latest), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                pluralStringResource(R.plurals.alarms_ringing, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 32.dp).heightIn(min = 80.dp)) {
                Text(stringResource(R.string.dismiss), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
