package io.github.allisson.bitcoinalert.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.allisson.bitcoinalert.domain.model.Timeframe
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val TIMEFRAME = stringPreferencesKey("timeframe")
private val MONITORING_REQUESTED = booleanPreferencesKey("monitoringRequested")

/** Preferences in the single process-scoped DataStore (spec.md §3.5). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val preferences = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** The selected timeframe, `1h` when nothing (or an unknown value) is stored. */
    val timeframe: Flow<Timeframe> = preferences
        .map { prefs -> Timeframe.entries.firstOrNull { it.binanceInterval == prefs[TIMEFRAME] } ?: Timeframe.H1 }
        .distinctUntilChanged()

    suspend fun setTimeframe(timeframe: Timeframe) {
        dataStore.edit { it[TIMEFRAME] = timeframe.binanceInterval }
    }

    /** `true` from the user's start until the user's stop (spec.md §2.8); `false` with nothing stored. */
    val monitoringRequested: Flow<Boolean> = preferences.map { it[MONITORING_REQUESTED] ?: false }.distinctUntilChanged()

    suspend fun setMonitoringRequested(requested: Boolean) {
        dataStore.edit { it[MONITORING_REQUESTED] = requested }
    }
}
