package io.github.allisson.bitcoinalert.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.allisson.bitcoinalert.MainActivity
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.service.PriceMonitorService
import io.github.allisson.bitcoinalert.ui.DateFormats
import io.github.allisson.bitcoinalert.ui.home.formatPrice

const val MONITORING_CHANNEL_ID = "monitoring"
const val ALERTS_CHANNEL_ID = "price_alerts"
const val MONITORING_NOTIFICATION_ID = 1

/** Every alert notification shares this ID; its history entry id is the tag (spec.md §2.6). */
const val ALERT_NOTIFICATION_ID = 2

/** The screen [MainActivity] opens: [OPEN_HOME] or [OPEN_HISTORY]. */
const val EXTRA_OPEN = "io.github.allisson.bitcoinalert.extra.OPEN"
const val OPEN_HOME = "home"
const val OPEN_HISTORY = "history"

// The request code tells the two PendingIntents apart: Android ignores extras when matching them.
private fun openScreen(context: Context, screen: String, requestCode: Int) = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_OPEN, screen)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE,
)

/**
 * Both channels exist from the first launch, so the user can adjust them in the system settings
 * before any alert triggers. Sound and vibration are channel properties: once created, only the
 * user changes them (spec.md §2.6).
 */
fun createNotificationChannels(context: Context) {
    NotificationManagerCompat.from(context).createNotificationChannelsCompat(
        listOf(
            NotificationChannelCompat.Builder(MONITORING_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_monitoring))
                .build(),
            NotificationChannelCompat.Builder(ALERTS_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_alerts))
                .setVibrationEnabled(true)
                .build(),
        ),
    )
}

/** The persistent notification; [activeAlerts] is `null` until the alerts are read. */
fun monitoringNotification(context: Context, activeAlerts: Int?): Notification {
    val stop = PendingIntent.getService(
        context,
        0,
        Intent(context, PriceMonitorService::class.java).setAction(PriceMonitorService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, MONITORING_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_market)
        .setContentTitle(context.getString(R.string.monitoring_notification_title))
        .apply {
            if (activeAlerts != null) {
                setContentText(context.resources.getQuantityString(R.plurals.active_alerts, activeAlerts, activeAlerts))
            }
        }
        .setContentIntent(openScreen(context, OPEN_HOME, 0))
        .addAction(0, context.getString(R.string.stop), stop)
        .setOngoing(true)
        .setShowWhen(false)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
}

/** "BTC above 100,000.00" / "Observed 100,012.34 at 14:32", in the device locale, time zone and 12/24-hour setting. */
fun alertNotification(context: Context, entry: AlertHistoryEntry): Notification {
    val locale = context.resources.configuration.locales[0]
    val title = if (entry.direction == AlertDirection.ABOVE) R.string.alert_notification_above else R.string.alert_notification_below
    val time = DateFormats(locale, DateFormat.is24HourFormat(context)).time(entry.triggeredAt)
    return NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_alerts)
        .setContentTitle(context.getString(title, formatPrice(entry.targetPrice, locale)))
        .setContentText(context.getString(R.string.alert_notification_text, formatPrice(entry.observedPrice, locale), time))
        .setWhen(entry.triggeredAt)
        .setShowWhen(true)
        .setContentIntent(openScreen(context, OPEN_HISTORY, 1))
        .setAutoCancel(true)
        .build()
}
