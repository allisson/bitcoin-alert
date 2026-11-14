package io.github.allisson.bitcoinalert.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import io.github.allisson.bitcoinalert.TestApp
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.notifications.ALERTS_CHANNEL_ID
import io.github.allisson.bitcoinalert.notifications.ALERT_NOTIFICATION_ID
import io.github.allisson.bitcoinalert.notifications.MONITORING_NOTIFICATION_ID
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// minSdk and the newest SDK Robolectric runs (roadmap Phase 4: validate on minSdk and targetSdk).
@Config(application = TestApp::class, sdk = [26, Config.NEWEST_SDK])
class PriceMonitorServiceTest {
    private val app = RuntimeEnvironment.getApplication() as TestApp
    private val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))

    init {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** The service's coroutines run on the main looper; DataStore reads finish on another thread. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("condition not met")
    }

    private val services = mutableListOf<ServiceController<PriceMonitorService>>()

    /** Cancels each service's loop: otherwise it keeps running on the shared main looper into the next test. */
    @After
    fun destroyServices() = services.forEach { it.destroy() }

    /** Android restarting the sticky service after the process died (spec.md §2.8). */
    private fun restart(requested: Boolean): PriceMonitorService {
        runBlocking { app.settingsRepository.setMonitoringRequested(requested) }
        return Robolectric.buildService(PriceMonitorService::class.java).create().also(services::add).get()
            .also { it.onStartCommand(null, 0, 1) }
    }

    /** A ticker message from Binance, then a pause so the service's collector on the main looper sees it. */
    private fun price(value: String) {
        waitUntil { app.listener != null }
        app.listener!!.onMessage("""{"stream":"btcusdt@ticker","data":{"e":"24hrTicker","c":"$value","P":"0.00"}}""")
        waitUntil { app.binanceStreamClient.ticker.value?.lastPrice?.compareTo(BigDecimal(value)) == 0 }
        Thread.sleep(20)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The Active count reaches the monitoring notification after the loop subscribed to the ticker. */
    private fun startEvaluating(): PriceMonitorService = restart(requested = true).also {
        waitUntil { notifications.getNotification(MONITORING_NOTIFICATION_ID)?.extras?.getCharSequence(Notification.EXTRA_TEXT) != null }
    }

    private fun alertNotifications() = notifications.allNotifications.filter { it.channelId == ALERTS_CHANNEL_ID }

    @Test
    fun `alerts crossed by the same price each post their own notification after the trigger`() {
        val ids = runBlocking { listOf("100.00", "100.50").map { app.alertRepository.create(ABOVE, BigDecimal(it)).id } }
        startEvaluating()
        price("99.00")
        price("101.00")
        waitUntil { alertNotifications().size == 2 }
        val history = runBlocking { app.alertRepository.history.first() }
        assertEquals(ids.toSet(), history.map { it.alertId }.toSet())
        history.forEach { assertNotNull(notifications.getNotification(it.id.toString(), ALERT_NOTIFICATION_ID)) }
    }

    @Test
    fun `a ticker received before the start is not the baseline`() {
        runBlocking { app.alertRepository.create(ABOVE, BigDecimal("100.00")) }
        app.homeVisible.value = true
        price("99.00")
        startEvaluating()
        price("101.00")
        price("102.00")
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<Notification>(), alertNotifications())
        assertEquals(0, runBlocking { app.alertRepository.history.first() }.size)
    }

    @Test
    fun `a lost connection keeps the loop and the baseline, so a crossing across the reconnection notifies`() {
        runBlocking { app.alertRepository.create(ABOVE, BigDecimal("100.00")) }
        val service = startEvaluating()
        price("99.00")
        val lost = app.listener!!
        lost.onClosed()
        // The client reconnects after its backoff delay with a new connection.
        waitUntil { app.listener !== lost }
        app.listener!!.onOpen()
        price("101.00")
        waitUntil { alertNotifications().size == 1 }
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `a database failure in the trigger stops the service instead of crashing`() {
        val id = runBlocking { app.alertRepository.create(ABOVE, BigDecimal("100.00")).id }
        // A real SQLiteException from inside the trigger transaction, as a full disk would raise.
        app.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail BEFORE INSERT ON alert_history BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )
        val service = startEvaluating()
        price("99.00")
        price("101.00")
        waitUntil { shadowOf(service).isStoppedBySelf }
        assertEquals(emptyList<Notification>(), alertNotifications())
        assertTrue(runBlocking { app.settingsRepository.monitoringRequested.first() })
        // The transaction rolled back: the alert is still Active.
        assertEquals(AlertState.Active, runBlocking { app.alertRepository.alerts.first() }.single { it.id == id }.state)
    }

    @Test
    fun `a restart with monitoring requested goes foreground and keeps running`() {
        val service = restart(requested = true)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        waitUntil { service.monitoring != null }
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertTrue(app.monitoringController.serviceRunning.value)
    }

    @Test
    fun `a restart after the user stopped goes foreground, then stops itself`() {
        val service = restart(requested = false)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        waitUntil { shadowOf(service).isStoppedBySelf }
        assertNull(service.monitoring)
    }

    @Test
    fun `a repeated start keeps one monitoring loop`() {
        val service = restart(requested = true)
        waitUntil { service.monitoring != null }
        val first = service.monitoring
        service.onStartCommand(Intent(app, PriceMonitorService::class.java), 0, 2)
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(first, service.monitoring)
    }

    @Test
    fun `the notification's Stop action clears the request, then stops the service`() {
        val service = restart(requested = true)
        waitUntil { service.monitoring != null }
        service.onStartCommand(Intent(app, PriceMonitorService::class.java).setAction(PriceMonitorService.ACTION_STOP), 0, 2)
        waitUntil { shadowOf(app).nextStoppedService != null }
        assertFalse(runBlocking { app.settingsRepository.monitoringRequested.first() })
    }

    @Test
    fun `the running flag follows onCreate and onDestroy`() {
        val controller = Robolectric.buildService(PriceMonitorService::class.java).create()
        assertTrue(app.monitoringController.serviceRunning.value)
        controller.destroy()
        assertFalse(app.monitoringController.serviceRunning.value)
    }
}
