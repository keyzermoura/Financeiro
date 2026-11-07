package br.com.carteirainvest.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.carteirainvest.data.local.AppDatabase
import br.com.carteirainvest.data.local.entity.CashTransactionEntity
import br.com.carteirainvest.data.local.entity.UserEntity
import br.com.carteirainvest.data.local.entity.WalletEntity
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetRepositoryImplTest {

    @Test
    fun initialAndSubsequentPurchasesSynchronizeCashAndKeepOneLinkWhenUpdatedOrDeleted() = runBlocking {
        val database = createDatabase()
        try {
            seedWallet(database)
            database.cashTransactionDao().insert(
                cashEntry(
                    id = "opening-1",
                    type = CashTransactionType.OPENING_BALANCE,
                    amount = "500.00",
                    date = "2026-01-01",
                    createdAt = 1,
                ),
            )
            val repository = repository(database)
            val asset = asset()
            val initialPurchase = purchase(
                id = "buy-initial",
                quantity = "2",
                unitPrice = "50",
                fees = "3.50",
                date = "2026-01-02",
                createdAt = 2,
            )

            repository.createAsset(asset, initialPurchase)

            val initialCash = database.cashTransactionDao()
                .findBySourceTransaction("buy-initial")
            assertEquals(CashTransactionType.WITHDRAWAL, initialCash?.type)
            assertDecimalEquals("103.50", initialCash?.amount)

            val laterPurchase = purchase(
                id = "buy-later",
                quantity = "1",
                unitPrice = "20",
                fees = "1.25",
                date = "2026-01-03",
                createdAt = 3,
            )
            repository.addTransaction(laterPurchase)
            assertDecimalEquals(
                "21.25",
                database.cashTransactionDao().findBySourceTransaction("buy-later")?.amount,
            )

            repository.updateTransaction(initialPurchase.copy(quantity = BigDecimal("3"), fees = BigDecimal("4")))
            val linkedEntries = database.cashTransactionDao().getByWallet("wallet-1")
                .filter { it.sourceTransactionId == "buy-initial" }
            assertEquals(1, linkedEntries.size)
            assertDecimalEquals("154", linkedEntries.single().amount)

            repository.deleteTransaction("buy-initial")
            assertNull(database.cashTransactionDao().findBySourceTransaction("buy-initial"))
            assertTrue(database.cashTransactionDao().findBySourceTransaction("buy-later") != null)
        } finally {
            database.close()
        }
    }

    @Test
    fun retroactivePurchaseThatMakesLaterCashBalanceNegativeRollsBackInvestmentAndCash() = runBlocking {
        val database = createDatabase()
        try {
            seedWallet(database)
            database.cashTransactionDao().insert(
                cashEntry(
                    id = "opening-1",
                    type = CashTransactionType.OPENING_BALANCE,
                    amount = "100",
                    date = "2026-01-01",
                    createdAt = 1,
                ),
            )
            val repository = repository(database)
            repository.createAsset(
                asset(),
                purchase(
                    id = "buy-later",
                    quantity = "1",
                    unitPrice = "90",
                    fees = "0",
                    date = "2026-01-05",
                    createdAt = 5,
                ),
            )

            val error = runCatching {
                repository.addTransaction(
                    purchase(
                        id = "buy-retroactive",
                        quantity = "1",
                        unitPrice = "20",
                        fees = "0",
                        date = "2026-01-03",
                        createdAt = 3,
                    ),
                )
            }.exceptionOrNull()

            assertTrue(error is ValidationException)
            assertNull(database.transactionDao().findById("buy-retroactive"))
            assertNull(database.cashTransactionDao().findBySourceTransaction("buy-retroactive"))
            assertEquals(2, database.cashTransactionDao().getByWallet("wallet-1").size)
            assertTrue(database.cashTransactionDao().findBySourceTransaction("buy-later") != null)
        } finally {
            database.close()
        }
    }

    @Test
    fun initialPurchaseWithInsufficientCashRollsBackAssetAndInvestmentTransaction() = runBlocking {
        val database = createDatabase()
        try {
            seedWallet(database)
            database.cashTransactionDao().insert(
                cashEntry(
                    id = "opening-1",
                    type = CashTransactionType.OPENING_BALANCE,
                    amount = "50",
                    date = "2026-01-01",
                    createdAt = 1,
                ),
            )
            val error = runCatching {
                repository(database).createAsset(
                    asset(),
                    purchase(
                        id = "buy-initial-failed",
                        quantity = "1",
                        unitPrice = "60",
                        fees = "2",
                        date = "2026-01-02",
                        createdAt = 2,
                    ),
                )
            }.exceptionOrNull()

            assertTrue(error is ValidationException)
            assertNull(database.assetDao().findById("asset-1"))
            assertNull(database.transactionDao().findById("buy-initial-failed"))
            assertNull(database.cashTransactionDao().findBySourceTransaction("buy-initial-failed"))
            assertEquals(1, database.cashTransactionDao().getByWallet("wallet-1").size)
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun seedWallet(database: AppDatabase) {
        database.userDao().insert(
            UserEntity(
                id = "user-1",
                name = "Teste",
                email = "teste@example.com",
                passwordHash = "hash",
                passwordSalt = "salt",
                createdAt = 1,
            ),
        )
        database.walletDao().insert(
            WalletEntity(
                id = "wallet-1",
                userId = "user-1",
                name = "Carteira",
                description = "",
                createdAt = 1,
            ),
        )
    }

    private fun repository(database: AppDatabase) = AssetRepositoryImpl(
        database = database,
        assetDao = database.assetDao(),
        transactionDao = database.transactionDao(),
        cashTransactionDao = database.cashTransactionDao(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun asset() = Asset(
        id = "asset-1",
        walletId = "wallet-1",
        ticker = "ABCD3",
        name = "Ativo de teste",
        type = AssetType.STOCK,
        createdAt = 1,
    )

    private fun purchase(
        id: String,
        quantity: String,
        unitPrice: String,
        fees: String,
        date: String,
        createdAt: Long,
    ) = Transaction(
        id = id,
        assetId = "asset-1",
        type = TransactionType.BUY,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        fees = BigDecimal(fees),
        dividendAmount = null,
        date = LocalDate.parse(date),
        notes = "",
        createdAt = createdAt,
    )

    private fun cashEntry(
        id: String,
        type: CashTransactionType,
        amount: String,
        date: String,
        createdAt: Long,
    ) = CashTransactionEntity(
        id = id,
        walletId = "wallet-1",
        type = type,
        amount = BigDecimal(amount),
        date = LocalDate.parse(date),
        description = "Lançamento manual",
        createdAt = createdAt,
    )

    private fun assertDecimalEquals(expected: String, actual: BigDecimal?) {
        val value = actual ?: throw AssertionError("O valor de caixa não foi criado")
        assertEquals(0, BigDecimal(expected).compareTo(value))
    }
}
