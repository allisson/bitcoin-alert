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
import io.github.allisson.bitcoinalert.notifications.ALERTS_CHANNEL_ID

/** System settings that change with no callback, read on every Home `ON_RESUME` (spec.md §2.1). */
data class DeviceChecks(
    /** `POST_NOTIFICATIONS` granted (Android 13+) and app notifications on. */
    val notificationsAllowed: Boolean,
    val alertsChannelBlocked: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

/** The settings screen that fixes disabled notifications. */
enum class NotificationsFix { App, Channel }

data class HomeWarnings(val monitoringOff: Boolean, val notificationsFix: NotificationsFix?, val batteryAction: Boolean)

/** Both warnings need Active alerts; the battery action only needs the app not to be exempt. */
fun homeWarnings(monitoring: MonitoringUi, device: DeviceChecks): HomeWarnings {
    val active = monitoring.activeAlerts > 0
    return HomeWarnings(
        monitoringOff = active && monitoring.state != MonitoringState.Running,
        notificationsFix = when {
            !active -> null
            !device.notificationsAllowed -> NotificationsFix.App
            device.alertsChannelBlocked -> NotificationsFix.Channel
            else -> null
        },
        batteryAction = !device.ignoringBatteryOptimizations,
    )
}

fun deviceChecks(context: Context): DeviceChecks {
    val notifications = NotificationManagerCompat.from(context)
    val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return DeviceChecks(
        notificationsAllowed = permission && notifications.areNotificationsEnabled(),
        alertsChannelBlocked = notifications.getNotificationChannelCompat(ALERTS_CHANNEL_ID)?.importance == NotificationManagerCompat.IMPORTANCE_NONE,
        ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    )
}

fun notificationSettingsIntent(context: Context, fix: NotificationsFix): Intent = when (fix) {
    NotificationsFix.App -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    NotificationsFix.Channel -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, ALERTS_CHANNEL_ID)
}.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

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
