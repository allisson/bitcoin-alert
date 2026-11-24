package io.github.allisson.bitcoinalert.ui.home

import android.Manifest
import android.app.NotificationManager
import android.os.PowerManager
import io.github.allisson.bitcoinalert.TestApp
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Interrupted
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Running
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Stopped
import io.github.allisson.bitcoinalert.notifications.ALARMS_CHANNEL_ID
import io.github.allisson.bitcoinalert.notifications.ALERTS_CHANNEL_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class HomeWarningsTest {
    private val fine = DeviceChecks(notificationsAllowed = true, alertsChannelBlocked = false, ignoringBatteryOptimizations = true)

    private fun warnings(state: MonitoringState, active: Int, device: DeviceChecks = fine) = homeWarnings(MonitoringUi(state, active), device)

    @Test
    fun `the monitoring warning shows only with Active alerts while Stopped or Interrupted`() {
        assertTrue(warnings(Stopped, 1).monitoringOff)
        assertTrue(warnings(Interrupted, 2).monitoringOff)
        assertFalse(warnings(Running, 1).monitoringOff)
        assertFalse(warnings(Stopped, 0).monitoringOff)
        assertFalse(warnings(Interrupted, 0).monitoringOff)
    }

    @Test
    fun `the notifications warning names the settings screen of its cause, only with Active alerts`() {
        val denied = fine.copy(notificationsAllowed = false)
        val blocked = fine.copy(alertsChannelBlocked = true)
        assertEquals(NotificationsFix.App, warnings(Running, 1, denied).notificationsFix)
        assertEquals(NotificationsFix.AlertsChannel, warnings(Running, 1, blocked).notificationsFix)
        assertEquals(NotificationsFix.App, warnings(Running, 1, denied.copy(alertsChannelBlocked = true)).notificationsFix)
        assertNull(warnings(Running, 1).notificationsFix)
        assertNull(warnings(Running, 0, denied).notificationsFix)
        assertNull(warnings(Stopped, 0, blocked).notificationsFix)
    }

    private fun alarmWarnings(active: Int, alarms: Int, device: DeviceChecks) = homeWarnings(MonitoringUi(Running, active, alarms), device)

    @Test
    fun `a blocked Alarms channel raises the notifications warning only with an Active Alarm-delivery alert`() {
        val blocked = fine.copy(alarmsChannelBlocked = true)
        assertEquals(NotificationsFix.AlarmsChannel, alarmWarnings(2, 1, blocked).notificationsFix)
        assertNull(alarmWarnings(1, 0, blocked).notificationsFix)
        assertNull(alarmWarnings(0, 0, blocked).notificationsFix)
        // The Price alerts channel comes first: it needs no Alarm-delivery alert.
        assertEquals(NotificationsFix.AlertsChannel, alarmWarnings(2, 1, blocked.copy(alertsChannelBlocked = true)).notificationsFix)
    }

    @Test
    fun `the full-screen intent warning shows only with an Active Alarm-delivery alert and the permission revoked`() {
        val revoked = fine.copy(fullScreenIntentAllowed = false)
        assertTrue(alarmWarnings(1, 1, revoked).fullScreenOff)
        assertFalse(alarmWarnings(1, 0, revoked).fullScreenOff)
        assertFalse(alarmWarnings(1, 1, fine).fullScreenOff)
    }

    @Test
    fun `the battery action shows only while not exempt, with or without alerts`() {
        val optimized = fine.copy(ignoringBatteryOptimizations = false)
        assertTrue(warnings(Stopped, 0, optimized).batteryAction)
        assertTrue(warnings(Running, 3, optimized).batteryAction)
        assertFalse(warnings(Stopped, 0).batteryAction)
    }

    @Test
    fun `device checks read the permission, app notifications, both alert channels and the exemption`() {
        val app = RuntimeEnvironment.getApplication()
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        power.setIgnoringBatteryOptimizations(app.packageName, true)
        // Robolectric has no full-screen intent setting on Android 14+: see the Android 13 test below.
        assertEquals(DeviceChecks(true, false, true), deviceChecks(app).copy(fullScreenIntentAllowed = true))

        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(deviceChecks(app).notificationsAllowed)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.setNotificationsEnabled(false)
        assertFalse(deviceChecks(app).notificationsAllowed)

        notifications.setNotificationsEnabled(true)
        app.getSystemService(NotificationManager::class.java).getNotificationChannel(ALERTS_CHANNEL_ID).importance = NotificationManager.IMPORTANCE_NONE
        assertTrue(deviceChecks(app).alertsChannelBlocked)
        assertFalse(deviceChecks(app).alarmsChannelBlocked)
        app.getSystemService(NotificationManager::class.java).getNotificationChannel(ALARMS_CHANNEL_ID).importance = NotificationManager.IMPORTANCE_NONE
        assertTrue(deviceChecks(app).alarmsChannelBlocked)

        power.setIgnoringBatteryOptimizations(app.packageName, false)
        assertFalse(deviceChecks(app).ignoringBatteryOptimizations)
    }

    @Test
    @Config(sdk = [33])
    fun `device checks read whether full-screen intents are allowed`() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.USE_FULL_SCREEN_INTENT)
        assertTrue(deviceChecks(app).fullScreenIntentAllowed)
        shadowOf(app).denyPermissions(Manifest.permission.USE_FULL_SCREEN_INTENT)
        assertFalse(deviceChecks(app).fullScreenIntentAllowed)
    }
}
