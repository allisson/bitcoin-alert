package io.github.allisson.bitcoinalert.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.allisson.bitcoinalert.BitcoinAlertApp
import io.github.allisson.bitcoinalert.data.repository.AlertRepository
import io.github.allisson.bitcoinalert.domain.model.AlertDirection
import io.github.allisson.bitcoinalert.domain.model.Ticker
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [sections] is `null` until the alerts are read. */
data class AlertsUiState(val price: BigDecimal? = null, val sections: AlertSections? = null)

class AlertsViewModel(ticker: StateFlow<Ticker?>, private val repository: AlertRepository) : ViewModel() {
    val uiState: StateFlow<AlertsUiState> = combine(
        ticker,
        combine(repository.alerts, repository.history, ::alertSections),
    ) { t, sections -> AlertsUiState(t?.lastPrice, sections) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun create(direction: AlertDirection, target: BigDecimal) {
        viewModelScope.launch { repository.create(direction, target) }
    }

    /** The switch: enabling starts a new activation cycle. */
    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { if (enabled) repository.activate(id) else repository.disable(id) }
    }

    fun reactivate(id: String) {
        viewModelScope.launch { repository.activate(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BitcoinAlertApp
                AlertsViewModel(app.binanceStreamClient.ticker, app.alertRepository)
            }
        }
    }
}
