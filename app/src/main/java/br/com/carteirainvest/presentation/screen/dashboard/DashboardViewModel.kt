package br.com.carteirainvest.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.usecase.market.RefreshWalletQuotesUseCase
import br.com.carteirainvest.domain.usecase.portfolio.ObservePortfolioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val portfolio: PortfolioSummary = PortfolioSummary.empty(null),
    val isRefreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observePortfolio: ObservePortfolioUseCase,
    private val refreshWalletQuotes: RefreshWalletQuotesUseCase,
    walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val selectedWalletId = walletSelectionManager.selectedWalletId
    private val portfolioFlow = selectedWalletId.flatMapLatest { walletId ->
        observePortfolio(walletId)
    }
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(portfolioFlow, refreshing) { portfolio, refreshing ->
        DashboardUiState(portfolio = portfolio, isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            selectedWalletId.filterNotNull().distinctUntilChanged().collectLatest { walletId ->
                refresh(walletId, force = false)
            }
        }
    }

    fun refresh() {
        val walletId = selectedWalletId.value ?: return
        viewModelScope.launch { refresh(walletId, force = true) }
    }

    private suspend fun refresh(walletId: String, force: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        try {
            when (val result = refreshWalletQuotes(walletId, force)) {
                is Resource.Success -> {
                    val summary = result.data
                    val messages = summary.messages
                    when {
                        messages.isNotEmpty() -> _messages.emit(messages.first())
                        result.isStale -> _messages.emit(
                            "Sem conexão. Exibindo cotações salvas.",
                        )
                        force -> _messages.emit(
                            if (summary.updated > 0) "Cotações atualizadas."
                            else "Cotações já estão atualizadas.",
                        )
                    }
                }

                is Resource.Failure -> _messages.emit(
                    result.error.userMessage("atualizar as cotações"),
                )
            }
        } catch (error: Exception) {
            _messages.emit(error.toUserMessage("atualizar as cotações"))
        } finally {
            refreshing.value = false
        }
    }
}
