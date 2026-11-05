package br.com.carteirainvest.domain.usecase.wallet

import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.repository.CashRepository
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class ObserveCashTransactionsUseCase @Inject constructor(
    private val cashRepository: CashRepository,
) {
    operator fun invoke(walletId: String): Flow<List<CashTransaction>> =
        cashRepository.observeTransactions(walletId)
}

class GetCashBalanceUseCase @Inject constructor(
    private val cashRepository: CashRepository,
) {
    suspend operator fun invoke(walletId: String): BigDecimal =
        cashRepository.getTransactions(walletId).fold(BigDecimal.ZERO) { balance, transaction ->
            when (transaction.type) {
                CashTransactionType.OPENING_BALANCE, CashTransactionType.DEPOSIT ->
                    balance.add(transaction.amount)
                CashTransactionType.WITHDRAWAL -> balance.subtract(transaction.amount)
            }
        }
}

class CreateOpeningCashBalanceUseCase @Inject constructor(
    private val cashRepository: CashRepository,
) {
    suspend operator fun invoke(
        walletId: String,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        description: String = "Saldo inicial",
    ) {
        validateAmount(amount, allowZero = true)
        validateDate(date)
        cashRepository.addTransaction(
            CashTransaction(
                id = UUID.randomUUID().toString(),
                walletId = walletId,
                type = CashTransactionType.OPENING_BALANCE,
                amount = amount,
                date = date,
                description = description.trim().ifBlank { "Saldo inicial" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}

class DepositCashUseCase @Inject constructor(
    private val cashRepository: CashRepository,
) {
    suspend operator fun invoke(
        walletId: String,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        description: String = "",
    ) {
        validateAmount(amount)
        validateDate(date)
        cashRepository.addTransaction(
            CashTransaction(
                id = UUID.randomUUID().toString(),
                walletId = walletId,
                type = CashTransactionType.DEPOSIT,
                amount = amount,
                date = date,
                description = description.trim().ifBlank { "Aporte de caixa" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}

class WithdrawCashUseCase @Inject constructor(
    private val cashRepository: CashRepository,
) {
    suspend operator fun invoke(
        walletId: String,
        amount: BigDecimal,
        date: LocalDate = LocalDate.now(),
        description: String = "",
    ) {
        validateAmount(amount)
        validateDate(date)
        cashRepository.addTransaction(
            CashTransaction(
                id = UUID.randomUUID().toString(),
                walletId = walletId,
                type = CashTransactionType.WITHDRAWAL,
                amount = amount,
                date = date,
                description = description.trim().ifBlank { "Retirada de caixa" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}

private fun validateAmount(amount: BigDecimal, allowZero: Boolean = false) {
    val valid = if (allowZero) amount >= BigDecimal.ZERO else amount > BigDecimal.ZERO
    if (!valid) {
        throw ValidationException(
            if (allowZero) "O valor do saldo inicial não pode ser negativo."
            else "Informe um valor maior que zero.",
        )
    }
}

private fun validateDate(date: LocalDate) {
    if (date.isAfter(LocalDate.now())) {
        throw ValidationException("A data do lançamento não pode estar no futuro.")
    }
}
