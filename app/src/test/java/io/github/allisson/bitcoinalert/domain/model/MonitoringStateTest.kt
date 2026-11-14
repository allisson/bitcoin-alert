package io.github.allisson.bitcoinalert.domain.model

import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Interrupted
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Running
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Stopped
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStateTest {
    @Test
    fun `state follows the request flag and the running service`() {
        assertEquals(Stopped, MonitoringState.of(requested = false, serviceRunning = false))
        // Between the stop's flag write and the service's onDestroy.
        assertEquals(Stopped, MonitoringState.of(requested = false, serviceRunning = true))
        assertEquals(Running, MonitoringState.of(requested = true, serviceRunning = true))
        assertEquals(Interrupted, MonitoringState.of(requested = true, serviceRunning = false))
    }

    @Test
    fun `Start shows while Stopped, Stop while Running, both while Interrupted`() {
        assertEquals(listOf(true to false, false to true, true to true), listOf(Stopped, Running, Interrupted).map { it.canStart to it.canStop })
    }
}
