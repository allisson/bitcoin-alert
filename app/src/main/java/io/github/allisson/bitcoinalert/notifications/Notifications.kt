package io.github.allisson.bitcoinalert.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.allisson.bitcoinalert.MainActivity
import io.github.allisson.bitcoinalert.R
import io.github.allisson.bitcoinalert.service.PriceMonitorService

const val MONITORING_CHANNEL_ID = "monitoring"
const val ALERTS_CHANNEL_ID = "price_alerts"
const val MONITORING_NOTIFICATION_ID = 1

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
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )
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
        .setContentIntent(open)
        .addAction(0, context.getString(R.string.stop), stop)
        .setOngoing(true)
        .setShowWhen(false)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()
}
