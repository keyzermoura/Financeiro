package br.com.carteirainvest.presentation.screen.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.usecase.wallet.CreateOpeningCashBalanceUseCase
import br.com.carteirainvest.domain.usecase.wallet.DepositCashUseCase
import br.com.carteirainvest.domain.usecase.wallet.ObserveCashTransactionsUseCase
import br.com.carteirainvest.domain.usecase.wallet.WithdrawCashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class CashUiState(
    val walletId: String? = null,
    val transactions: List<CashTransaction> = emptyList(),
    val balance: BigDecimal = BigDecimal.ZERO,
    val hasOpeningBalance: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashViewModel @Inject constructor(
    walletSelectionManager: WalletSelectionManager,
    observeCashTransactions: ObserveCashTransactionsUseCase,
    private val createOpeningBalance: CreateOpeningCashBalanceUseCase,
    private val depositCash: DepositCashUseCase,
    private val withdrawCash: WithdrawCashUseCase,
) : ViewModel() {
    val uiState: StateFlow<CashUiState> = walletSelectionManager.selectedWalletId.flatMapLatest { walletId ->
        if (walletId == null) flowOf(CashUiState())
        else observeCashTransactions(walletId).map { rows ->
            var balance = BigDecimal.ZERO
            rows.forEach { row ->
                balance = when (row.type) {
                    CashTransactionType.OPENING_BALANCE, CashTransactionType.DEPOSIT -> balance.add(row.amount)
                    CashTransactionType.WITHDRAWAL -> balance.subtract(row.amount)
                }
            }
            CashUiState(
                walletId = walletId,
                transactions = rows.sortedWith(compareByDescending<CashTransaction> { it.date }.thenByDescending { it.createdAt }),
                balance = balance,
                hasOpeningBalance = rows.any { it.type == CashTransactionType.OPENING_BALANCE },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun register(type: CashTransactionType, amount: BigDecimal, description: String) {
        val walletId = uiState.value.walletId ?: return
        viewModelScope.launch {
            runCatching {
                when (type) {
                    CashTransactionType.OPENING_BALANCE -> createOpeningBalance(walletId, amount, LocalDate.now(), description)
                    CashTransactionType.DEPOSIT -> depositCash(walletId, amount, LocalDate.now(), description)
                    CashTransactionType.WITHDRAWAL -> withdrawCash(walletId, amount, LocalDate.now(), description)
                }
            }.onSuccess {
                _messages.emit(
                    when (type) {
                        CashTransactionType.OPENING_BALANCE -> "Saldo inicial cadastrado."
                        CashTransactionType.DEPOSIT -> "Aporte registrado."
                        CashTransactionType.WITHDRAWAL -> "Retirada registrada."
                    },
                )
            }.onFailure { _messages.emit(it.toUserMessage("registrar movimentação de caixa")) }
        }
    }
}
