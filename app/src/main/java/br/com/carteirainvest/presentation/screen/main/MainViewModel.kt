package br.com.carteirainvest.presentation.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.network.NetworkMonitor
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.Wallet
import br.com.carteirainvest.domain.usecase.auth.ObserveSessionUseCase
import br.com.carteirainvest.domain.usecase.wallet.CreateWalletUseCase
import br.com.carteirainvest.domain.usecase.wallet.DeleteWalletUseCase
import br.com.carteirainvest.domain.usecase.wallet.ObserveWalletsUseCase
import br.com.carteirainvest.domain.usecase.wallet.UpdateWalletUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val userName: String = "",
    val userEmail: String = "",
    val wallets: List<Wallet> = emptyList(),
    val selectedWallet: Wallet? = null,
    val isOnline: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val observeWallets: ObserveWalletsUseCase,
    private val createWalletUseCase: CreateWalletUseCase,
    private val updateWalletUseCase: UpdateWalletUseCase,
    private val deleteWalletUseCase: DeleteWalletUseCase,
    private val walletSelectionManager: WalletSelectionManager,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val sessionFlow = observeSession()
    private val userIdFlow = sessionFlow.map { it?.userId }.distinctUntilChanged()
    private val walletsFlow = userIdFlow.flatMapLatest { userId ->
        if (userId == null) flowOf(emptyList()) else observeWallets(userId)
    }
    private val onlineFlow = networkMonitor.observe()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        sessionFlow,
        walletsFlow,
        walletSelectionManager.selectedWalletId,
        onlineFlow,
    ) { session, wallets, selectedId, online ->
        val selected = wallets.firstOrNull { it.id == selectedId } ?: wallets.firstOrNull()
        MainUiState(
            userName = session?.name.orEmpty(),
            userEmail = session?.email.orEmpty(),
            wallets = wallets,
            selectedWallet = selected,
            isOnline = online,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            combine(walletsFlow, walletSelectionManager.selectedWalletId) { wallets, selectedId ->
                wallets to selectedId
            }.collect { (wallets, selectedId) ->
                when {
                    wallets.isEmpty() -> if (selectedId != null) walletSelectionManager.select(null)
                    selectedId == null -> walletSelectionManager.select(wallets.first().id)
                    wallets.none { it.id == selectedId } -> walletSelectionManager.select(wallets.first().id)
                }
            }
        }
    }

    fun selectWallet(walletId: String) {
        walletSelectionManager.select(walletId)
    }

    fun createWallet(name: String, description: String) {
        val userId = sessionFlow.value?.userId ?: return
        viewModelScope.launch {
            runCatching { createWalletUseCase(userId, name, description) }
                .onSuccess { wallet ->
                    walletSelectionManager.select(wallet.id)
                    _messages.emit("Carteira \"${wallet.name}\" criada.")
                }
                .onFailure { error ->
                    _messages.emit(error.toUserMessage("criar a carteira"))
                }
        }
    }

    fun updateWallet(wallet: Wallet, name: String, description: String) {
        viewModelScope.launch {
            runCatching { updateWalletUseCase(wallet, name, description) }
                .onSuccess { _messages.emit("Carteira atualizada.") }
                .onFailure { error -> _messages.emit(error.toUserMessage("atualizar a carteira")) }
        }
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            runCatching { deleteWalletUseCase(walletId) }
                .onSuccess { _messages.emit("Carteira excluída.") }
                .onFailure { error -> _messages.emit(error.toUserMessage("excluir a carteira")) }
        }
    }

    fun formatWalletSubtitle(wallet: Wallet): String =
        wallet.description.ifBlank { "Criada em ${BrFormat.date(wallet.createdAt.toLocalDate())}" }

    private fun Long.toLocalDate(): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(this)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
}
