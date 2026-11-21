package io.github.allisson.bitcoinalert.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.notifications.ALARMS_CHANNEL_ID
import io.github.allisson.bitcoinalert.notifications.ALERTS_CHANNEL_ID

/** System settings that change with no callback, read on every Home `ON_RESUME` (spec.md §2.1). */
data class DeviceChecks(
    /** `POST_NOTIFICATIONS` granted (Android 13+) and app notifications on. */
    val notificationsAllowed: Boolean,
    val alertsChannelBlocked: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val alarmsChannelBlocked: Boolean = false,
    /** `canUseFullScreenIntent()`: always `true` below Android 14. */
    val fullScreenIntentAllowed: Boolean = true,
)

/** The settings screen that fixes disabled notifications. */
enum class NotificationsFix { App, AlertsChannel, AlarmsChannel }

data class HomeWarnings(
    val monitoringOff: Boolean,
    val notificationsFix: NotificationsFix?,
    val batteryAction: Boolean,
    val fullScreenOff: Boolean = false,
)

/**
 * The warnings need Active alerts, the Alarms channel and full-screen ones an Active alert with
 * the Alarm delivery; the battery action only needs the app not to be exempt.
 */
fun homeWarnings(monitoring: MonitoringUi, device: DeviceChecks): HomeWarnings {
    val active = monitoring.activeAlerts > 0
    val alarms = monitoring.activeAlarms > 0
    return HomeWarnings(
        monitoringOff = active && monitoring.state != MonitoringState.Running,
        notificationsFix = when {
            !active -> null
            !device.notificationsAllowed -> NotificationsFix.App
            device.alertsChannelBlocked -> NotificationsFix.AlertsChannel
            alarms && device.alarmsChannelBlocked -> NotificationsFix.AlarmsChannel
            else -> null
        },
        batteryAction = !device.ignoringBatteryOptimizations,
        fullScreenOff = alarms && !device.fullScreenIntentAllowed,
    )
}

fun deviceChecks(context: Context): DeviceChecks {
    val notifications = NotificationManagerCompat.from(context)
    val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return DeviceChecks(
        notificationsAllowed = permission && notifications.areNotificationsEnabled(),
        alertsChannelBlocked = notifications.channelBlocked(ALERTS_CHANNEL_ID),
        ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        alarmsChannelBlocked = notifications.channelBlocked(ALARMS_CHANNEL_ID),
        fullScreenIntentAllowed = notifications.canUseFullScreenIntent(),
    )
}

private fun NotificationManagerCompat.channelBlocked(id: String) =
    getNotificationChannelCompat(id)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

fun notificationSettingsIntent(context: Context, fix: NotificationsFix): Intent = when (fix) {
    NotificationsFix.App -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    NotificationsFix.AlertsChannel -> channelSettings(ALERTS_CHANNEL_ID)
    NotificationsFix.AlarmsChannel -> channelSettings(ALARMS_CHANNEL_ID)
}.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun channelSettings(id: String) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, id)

// Only shown on Android 14+, where the setting exists: below, full-screen intents are always allowed.
@SuppressLint("InlinedApi")
fun fullScreenIntentSettingsIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))

// Offered only on the user's tap, never automatically (spec.md §2.8); the app is not on Google Play.
@SuppressLint("BatteryLife")
fun batteryExemptionIntent(context: Context) =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

/** Some devices lack a settings screen; the warning then stays, with no crash. */
fun Context.startSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("HomeWarnings", "No activity for ${intent.action}", e)
    }
}
