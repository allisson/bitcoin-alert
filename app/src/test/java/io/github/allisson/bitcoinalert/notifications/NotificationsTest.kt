package io.github.allisson.bitcoinalert.notifications

import android.app.Notification
import io.github.allisson.bitcoinalert.TestApp
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import java.math.BigDecimal
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
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
}
