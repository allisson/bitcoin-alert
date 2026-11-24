package io.github.allisson.bitcoinalert.notifications

import android.app.Notification
import io.github.allisson.bitcoinalert.TestApp
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import io.github.allisson.bitcoinalert.service.AlarmNotice
import io.github.allisson.bitcoinalert.service.PriceMonitorService
import io.github.allisson.bitcoinalert.ui.alarm.AlarmActivity
import java.math.BigDecimal
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, qualifiers = "en-rUS")
class NotificationsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val zone = TimeZone.getDefault()

    @Before
    fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restore() = TimeZone.setDefault(zone)

    // 2026-11-13 14:32 UTC
    private fun entry(direction: AlertDirection) =
        AlertHistoryEntry(7, "a", BigDecimal("100000.00"), BigDecimal("100012.34"), direction, 1_794_580_320_000)

    private fun Notification.text(key: String) = extras.getCharSequence(key).toString().replace(' ', ' ')

    @Test
    fun `an alert notification names the direction, target, observed price and trigger time`() {
        val notification = alertNotification(app, entry(AlertDirection.ABOVE))
        assertEquals("BTC above 100,000.00", notification.text(Notification.EXTRA_TITLE))
        assertEquals("Observed 100,012.34 at 2:32 PM", notification.text(Notification.EXTRA_TEXT))
        assertEquals("BTC below 100,000.00", alertNotification(app, entry(AlertDirection.BELOW)).text(Notification.EXTRA_TITLE))
        assertEquals(1_794_580_320_000, notification.`when`)
        assertEquals(ALERTS_CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `tapping opens History for an alert and Home for monitoring`() {
        val alert = shadowOf(alertNotification(app, entry(AlertDirection.ABOVE)).contentIntent).savedIntent
        assertEquals(OPEN_HISTORY, alert.getStringExtra(EXTRA_OPEN))
        val monitoring = shadowOf(monitoringNotification(app, 1).contentIntent).savedIntent
        assertEquals(OPEN_HOME, monitoring.getStringExtra(EXTRA_OPEN))
    }

    private val Notification.ongoing get() = flags and Notification.FLAG_ONGOING_EVENT != 0

    @Test
    fun `a ringing alarm is ongoing, with Dismiss and the full-screen alarm screen`() {
        val notification = alarmNotification(app, entry(AlertDirection.ABOVE), AlarmNotice.Ringing)
        assertEquals(ALARMS_CHANNEL_ID, notification.channelId)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals("BTC above 100,000.00", notification.text(Notification.EXTRA_TITLE))
        assertTrue(notification.ongoing)
        assertEquals(AlarmActivity::class.java.name, shadowOf(notification.fullScreenIntent).savedIntent.component?.className)
        val dismiss = notification.actions.single()
        assertEquals("Dismiss", dismiss.title)
        assertEquals(PriceMonitorService.ACTION_DISMISS_ALARM, shadowOf(dismiss.actionIntent).savedIntent.action)
    }

    @Test
    fun `a dismissed or missed alarm is a regular notification that opens History`() {
        val dismissed = alarmNotification(app, entry(AlertDirection.ABOVE), AlarmNotice.Dismissed)
        val missed = alarmNotification(app, entry(AlertDirection.BELOW), AlarmNotice.Missed)
        assertEquals("BTC above 100,000.00", dismissed.text(Notification.EXTRA_TITLE))
        assertEquals("Missed: BTC below 100,000.00", missed.text(Notification.EXTRA_TITLE))
        assertEquals("Observed 100,012.34 at 2:32 PM", missed.text(Notification.EXTRA_TEXT))
        for (notification in listOf(dismissed, missed)) {
            assertFalse(notification.ongoing)
            assertNull(notification.fullScreenIntent)
            assertNull(notification.actions)
            assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
            assertEquals(OPEN_HISTORY, shadowOf(notification.contentIntent).savedIntent.getStringExtra(EXTRA_OPEN))
        }
    }
}
