package io.github.allisson.bitcoinalert.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }

    @Test
    fun `timeframe is 1h with nothing stored`() = runTest {
        assertEquals(Timeframe.H1, SettingsRepository(dataStore()).timeframe.first())
    }

    @Test
    fun `selected timeframe is read back`() = runTest {
        val settings = SettingsRepository(dataStore())
        settings.setTimeframe(Timeframe.M15)
        assertEquals(Timeframe.M15, settings.timeframe.first())
    }

    @Test
    fun `an unknown stored value falls back to 1h`() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("timeframe")] = "3m" }
        assertEquals(Timeframe.H1, SettingsRepository(store).timeframe.first())
    }

    @Test
    fun `monitoring is not requested with nothing stored`() = runTest {
        assertEquals(false, SettingsRepository(dataStore()).monitoringRequested.first())
    }

    @Test
    fun `monitoring request is read back`() = runTest {
        val settings = SettingsRepository(dataStore())
        settings.setMonitoringRequested(true)
        assertEquals(true, settings.monitoringRequested.first())
    }
}
