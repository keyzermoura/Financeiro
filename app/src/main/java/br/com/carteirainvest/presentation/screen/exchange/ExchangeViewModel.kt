package br.com.carteirainvest.presentation.screen.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.usecase.exchange.ObserveExchangeRatesUseCase
import br.com.carteirainvest.domain.usecase.exchange.RefreshExchangeRatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExchangeUiState(
    val rates: List<ExchangeRate> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ExchangeViewModel @Inject constructor(
    observeRates: ObserveExchangeRatesUseCase,
    private val refreshRates: RefreshExchangeRatesUseCase,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val loadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExchangeUiState> = combine(
        observeRates(),
        refreshing,
        loadError,
    ) { rates, refreshing, error ->
        ExchangeUiState(rates = rates, isRefreshing = refreshing, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExchangeUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { refresh(force = false, quiet = true) }
    }

    fun refresh() {
        viewModelScope.launch { refresh(force = true, quiet = false) }
    }

    private suspend fun refresh(force: Boolean, quiet: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        try {
            when (val result = refreshRates(force)) {
                is Resource.Success -> {
                    loadError.value = if (result.isStale) {
                        "Sem conexão. Exibindo as últimas taxas salvas."
                    } else {
                        null
                    }
                    if (!quiet) {
                        _messages.emit(
                            if (result.isStale) {
                                "Sem conexão. Exibindo as últimas taxas salvas."
                            } else {
                                "Taxas de câmbio atualizadas."
                            },
                        )
                    }
                }

                is Resource.Failure -> {
                    val message = result.error.userMessage("atualizar as taxas de câmbio")
                    loadError.value = message
                    if (!quiet) _messages.emit(message)
                }
            }
        } catch (error: Exception) {
            val message = error.toUserMessage("atualizar as taxas de câmbio")
            loadError.value = message
            if (!quiet) _messages.emit(message)
        } finally {
            refreshing.value = false
        }
    }
}
