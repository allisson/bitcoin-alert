package io.github.allisson.bitcoinalert.service

import io.github.allisson.bitcoinalert.data.repository.SettingsRepository
import io.github.allisson.bitcoinalert.domain.model.MonitoringState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Starts and stops monitoring, writing in the order that never derives Interrupted during a
 * normal start or stop (spec.md §2.8).
 */
class MonitoringController(
    private val settings: SettingsRepository,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
) {
    /**
     * The service is running: set by [start] once the start call returns and by the service's
     * `onCreate`, which can arrive after the `monitoringRequested` write; cleared in `onDestroy`.
     * In memory, so process death resets it and a killed service derives Interrupted.
     */
    val serviceRunning = MutableStateFlow(false)

    val state: Flow<MonitoringState> =
        combine(settings.monitoringRequested, serviceRunning, MonitoringState::of).distinctUntilChanged()

    /** Throws when Android refuses the start; nothing is written then. */
    suspend fun start() {
        startService()
        serviceRunning.value = true
        settings.setMonitoringRequested(true)
    }

    suspend fun stop() {
        settings.setMonitoringRequested(false)
        stopService()
    }
}
