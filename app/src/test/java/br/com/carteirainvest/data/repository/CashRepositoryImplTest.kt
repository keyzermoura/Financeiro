package br.com.carteirainvest.data.repository

import br.com.carteirainvest.data.local.dao.CashTransactionDao
import br.com.carteirainvest.data.local.entity.CashTransactionEntity
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CashRepositoryImplTest {

    @Test
    fun `allows opening balance and deposits while keeping wallet transactions isolated`() {
        runBlocking {
        val dao = FakeCashTransactionDao()
        val repository = CashRepositoryImpl(dao, Dispatchers.Unconfined)

        repository.addTransaction(transaction("wallet-1", CashTransactionType.OPENING_BALANCE, "1000", "2026-01-01"))
        repository.addTransaction(transaction("wallet-1", CashTransactionType.DEPOSIT, "250", "2026-01-02"))
        repository.addTransaction(transaction("wallet-2", CashTransactionType.DEPOSIT, "75", "2026-01-02"))

        val walletOne = repository.getTransactions("wallet-1")
        assertEquals(2, walletOne.size)
        assertEquals(0, BigDecimal("1250").compareTo(
            walletOne.fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) },
        ))
        assertEquals(1, repository.getTransactions("wallet-2").size)
    }
    }

    @Test
    fun `rejects a second opening balance`() {
        runBlocking {
        val repository = CashRepositoryImpl(FakeCashTransactionDao(), Dispatchers.Unconfined)
        repository.addTransaction(transaction("wallet-1", CashTransactionType.OPENING_BALANCE, "100", "2026-01-01"))

        assertThrows(ValidationException::class.java) {
            runBlocking {
                repository.addTransaction(
                    transaction("wallet-1", CashTransactionType.OPENING_BALANCE, "200", "2026-01-02"),
                )
            }
        }
    }
    }

    @Test
    fun `rejects withdrawal that makes chronological balance negative`() {
        runBlocking {
        val repository = CashRepositoryImpl(FakeCashTransactionDao(), Dispatchers.Unconfined)
        repository.addTransaction(transaction("wallet-1", CashTransactionType.OPENING_BALANCE, "100", "2026-01-01"))

        assertThrows(ValidationException::class.java) {
            runBlocking {
                repository.addTransaction(
                    transaction("wallet-1", CashTransactionType.WITHDRAWAL, "150", "2026-01-02"),
                )
            }
        }
    }
    }

    @Test
    fun `rejects retroactive deposit when an earlier withdrawal would remain uncovered`() {
        runBlocking {
        val repository = CashRepositoryImpl(FakeCashTransactionDao(), Dispatchers.Unconfined)
        repository.addTransaction(transaction("wallet-1", CashTransactionType.OPENING_BALANCE, "100", "2026-01-01"))
        repository.addTransaction(transaction("wallet-1", CashTransactionType.DEPOSIT, "100", "2026-01-03"))

        assertThrows(ValidationException::class.java) {
            runBlocking {
                repository.addTransaction(
                    transaction("wallet-1", CashTransactionType.WITHDRAWAL, "150", "2026-01-02"),
                )
            }
        }
    }
    }

    private fun transaction(
        walletId: String,
        type: CashTransactionType,
        amount: String,
        date: String,
    ) = CashTransaction(
        id = java.util.UUID.randomUUID().toString(),
        walletId = walletId,
        type = type,
        amount = BigDecimal(amount),
        date = LocalDate.parse(date),
        description = "Teste",
        createdAt = System.nanoTime(),
    )

    private class FakeCashTransactionDao : CashTransactionDao {
        private val rows = MutableStateFlow<List<CashTransactionEntity>>(emptyList())

        override fun observeByWallet(walletId: String): Flow<List<CashTransactionEntity>> =
            rows.map { list -> list.filter { it.walletId == walletId } }

        override suspend fun getByWallet(walletId: String): List<CashTransactionEntity> =
            rows.value.filter { it.walletId == walletId }
                .sortedWith(compareBy<CashTransactionEntity> { it.date }.thenBy { it.createdAt }.thenBy { it.id })

        override suspend fun insert(transaction: CashTransactionEntity) {
            rows.value = rows.value + transaction
        }

        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
