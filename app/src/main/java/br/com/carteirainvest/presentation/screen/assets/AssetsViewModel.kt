package br.com.carteirainvest.presentation.screen.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.usecase.market.RefreshWalletQuotesUseCase
import br.com.carteirainvest.domain.usecase.portfolio.ObserveAssetPositionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetsUiState(
    val positions: List<PortfolioPosition> = emptyList(),
    val isRefreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetsViewModel @Inject constructor(
    observeAssetPositions: ObserveAssetPositionsUseCase,
    private val refreshWalletQuotes: RefreshWalletQuotesUseCase,
    walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val selectedWalletId = walletSelectionManager.selectedWalletId
    private val positionsFlow = selectedWalletId.flatMapLatest { observeAssetPositions(it) }
    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<AssetsUiState> = combine(positionsFlow, refreshing) { positions, refreshing ->
        AssetsUiState(positions = positions, isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsUiState(),
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
                    when {
                        summary.messages.isNotEmpty() -> _messages.emit(summary.messages.first())
                        result.isStale -> _messages.emit(
                            "Sem conexão. Exibindo cotações salvas.",
                        )
                        force -> _messages.emit(
                            if (summary.updated > 0) "Cotações atualizadas."
                            else "As cotações já estão atualizadas.",
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
