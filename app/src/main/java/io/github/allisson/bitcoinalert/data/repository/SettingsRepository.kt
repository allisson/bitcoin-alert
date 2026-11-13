package io.github.allisson.bitcoinalert.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

/** Preferences in the single process-scoped DataStore (spec.md §3.5). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    /** The selected timeframe, `1h` when nothing (or an unknown value) is stored. */
    val timeframe: Flow<Timeframe> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> Timeframe.entries.firstOrNull { it.binanceInterval == prefs[TIMEFRAME] } ?: Timeframe.H1 }
        .distinctUntilChanged()

    suspend fun setTimeframe(timeframe: Timeframe) {
        dataStore.edit { it[TIMEFRAME] = timeframe.binanceInterval }
    }
}
