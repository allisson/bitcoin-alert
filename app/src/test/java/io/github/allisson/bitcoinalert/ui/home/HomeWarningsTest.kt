package io.github.allisson.bitcoinalert.ui.home

import android.Manifest
import android.app.NotificationManager
import android.os.PowerManager
import io.github.allisson.bitcoinalert.TestApp
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Interrupted
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Running
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Stopped
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
        assertEquals(NotificationsFix.Channel, warnings(Running, 1, blocked).notificationsFix)
        assertEquals(NotificationsFix.App, warnings(Running, 1, denied.copy(alertsChannelBlocked = true)).notificationsFix)
        assertNull(warnings(Running, 1).notificationsFix)
        assertNull(warnings(Running, 0, denied).notificationsFix)
        assertNull(warnings(Stopped, 0, blocked).notificationsFix)
    }

    @Test
    fun `the battery action shows only while not exempt, with or without alerts`() {
        val optimized = fine.copy(ignoringBatteryOptimizations = false)
        assertTrue(warnings(Stopped, 0, optimized).batteryAction)
        assertTrue(warnings(Running, 3, optimized).batteryAction)
        assertFalse(warnings(Stopped, 0).batteryAction)
    }

    @Test
    fun `device checks read the permission, app notifications, the alerts channel and the exemption`() {
        val app = RuntimeEnvironment.getApplication()
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        power.setIgnoringBatteryOptimizations(app.packageName, true)
        assertEquals(DeviceChecks(true, false, true), deviceChecks(app))

        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(deviceChecks(app).notificationsAllowed)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.setNotificationsEnabled(false)
        assertFalse(deviceChecks(app).notificationsAllowed)

        notifications.setNotificationsEnabled(true)
        app.getSystemService(NotificationManager::class.java).getNotificationChannel(ALERTS_CHANNEL_ID).importance = NotificationManager.IMPORTANCE_NONE
        assertTrue(deviceChecks(app).alertsChannelBlocked)

        power.setIgnoringBatteryOptimizations(app.packageName, false)
        assertFalse(deviceChecks(app).ignoringBatteryOptimizations)
    }
}
