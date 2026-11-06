package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.dao.CashTransactionDao
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.local.toEntity
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.repository.CashRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashRepositoryImpl @Inject constructor(
    private val cashTransactionDao: CashTransactionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CashRepository {

    private val writeMutex = Mutex()

    override fun observeTransactions(walletId: String): Flow<List<CashTransaction>> =
        cashTransactionDao.observeByWallet(walletId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTransactions(walletId: String): List<CashTransaction> =
        withContext(ioDispatcher) {
            cashTransactionDao.getByWallet(walletId).map { it.toDomain() }
        }

    override suspend fun addTransaction(transaction: CashTransaction) = withContext(ioDispatcher) {
        writeMutex.withLock {
            val current = cashTransactionDao.getByWallet(transaction.walletId).map { it.toDomain() }
            if (transaction.type == CashTransactionType.OPENING_BALANCE &&
                current.any { it.type == CashTransactionType.OPENING_BALANCE }
            ) {
                throw ValidationException("O saldo inicial desta carteira já foi cadastrado.")
            }
            val candidate = (current + transaction).sortedWith(
                compareBy<CashTransaction> { it.date }.thenBy { it.createdAt }.thenBy { it.id },
            )
            var runningBalance = BigDecimal.ZERO
            candidate.forEach { entry ->
                runningBalance = when (entry.type) {
                    CashTransactionType.OPENING_BALANCE,
                    CashTransactionType.DEPOSIT -> runningBalance.add(entry.amount)
                    CashTransactionType.WITHDRAWAL -> runningBalance.subtract(entry.amount)
                }
                if (runningBalance < BigDecimal.ZERO) {
                    throw ValidationException(
                        "Esta operação deixaria o saldo de caixa negativo em ${entry.date}.",
                    )
                }
            }
            cashTransactionDao.insert(transaction.toEntity())
        }
    }
}
