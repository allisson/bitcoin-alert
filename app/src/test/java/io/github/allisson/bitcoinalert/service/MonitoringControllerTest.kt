package io.github.allisson.bitcoinalert.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.allisson.bitcoinalert.data.repository.SettingsRepository
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Interrupted
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Running
import io.github.allisson.bitcoinalert.domain.model.MonitoringState.Stopped
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.settings() =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") })

    private fun TestScope.states(controller: MonitoringController): List<MonitoringState> {
        val states = mutableListOf<MonitoringState>()
        backgroundScope.launch { controller.state.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun `a start shows Running before the service's onCreate, never Interrupted`() = runTest {
        // The fake start never calls onCreate: the flag write completes first.
        val controller = MonitoringController(settings(), startService = {}, stopService = {})
        val states = states(controller)
        controller.start()
        runCurrent()
        assertEquals(listOf(Stopped, Running), states)
    }

    @Test
    fun `a stop goes from Running to Stopped even when the service is destroyed at once`() = runTest {
        lateinit var controller: MonitoringController
        controller = MonitoringController(settings(), startService = {}, stopService = { controller.serviceRunning.value = false })
        controller.start()
        val states = states(controller)
        controller.stop()
        runCurrent()
        assertEquals(listOf(Running, Stopped), states)
    }

    @Test
    fun `a refused start writes nothing`() = runTest {
        val settings = settings()
        val controller = MonitoringController(settings, startService = { throw IllegalStateException("refused") }, stopService = {})
        assertTrue(runCatching { controller.start() }.isFailure)
        assertFalse(controller.serviceRunning.value)
        assertFalse(settings.monitoringRequested.first())
    }

    @Test
    fun `a new process with monitoring requested derives Interrupted`() = runTest {
        val settings = settings()
        settings.setMonitoringRequested(true)
        assertEquals(Interrupted, MonitoringController(settings, {}, {}).state.first())
    }
}
