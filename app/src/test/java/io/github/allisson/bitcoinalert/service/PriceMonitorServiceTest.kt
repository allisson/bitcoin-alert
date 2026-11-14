package io.github.allisson.bitcoinalert.service

import android.content.Intent
import android.os.Looper
import io.github.allisson.bitcoinalert.TestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class PriceMonitorServiceTest {
    private val app = RuntimeEnvironment.getApplication() as TestApp

    /** The service's coroutines run on the main looper; DataStore reads finish on another thread. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("condition not met")
    }

    /** Android restarting the sticky service after the process died (spec.md §2.8). */
    private fun restart(requested: Boolean): PriceMonitorService {
        runBlocking { app.settingsRepository.setMonitoringRequested(requested) }
        return Robolectric.buildService(PriceMonitorService::class.java).create().get()
            .also { it.onStartCommand(null, 0, 1) }
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
