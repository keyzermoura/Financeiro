package br.com.carteirainvest.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.AppDatabase
import br.com.carteirainvest.data.local.dao.AssetDao
import br.com.carteirainvest.data.local.dao.CashTransactionDao
import br.com.carteirainvest.data.local.dao.TransactionDao
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.local.toEntity
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.CashTransaction
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.repository.AssetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val assetDao: AssetDao,
    private val transactionDao: TransactionDao,
    private val cashTransactionDao: CashTransactionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AssetRepository {

    override fun observeAssets(walletId: String): Flow<List<Asset>> =
        assetDao.observeByWallet(walletId).map { assets -> assets.map { it.toDomain() } }

    override fun observeAsset(id: String): Flow<Asset?> =
        assetDao.observeById(id).map { it?.toDomain() }

    override suspend fun getAssets(walletId: String): List<Asset> = withContext(ioDispatcher) {
        assetDao.getByWallet(walletId).map { it.toDomain() }
    }

    override suspend fun getAllAssets(): List<Asset> = withContext(ioDispatcher) {
        assetDao.getAll().map { it.toDomain() }
    }

    override suspend fun getAsset(id: String): Asset? = withContext(ioDispatcher) {
        assetDao.findById(id)?.toDomain()
    }

    override suspend fun findByWalletAndTicker(walletId: String, ticker: String): Asset? =
        withContext(ioDispatcher) {
            assetDao.findByWalletAndTicker(walletId, ticker)?.toDomain()
        }

    override suspend fun createAsset(asset: Asset, firstTransaction: Transaction?) {
        withContext(ioDispatcher) {
            database.withTransaction {
                try {
                    assetDao.insert(asset.toEntity())
                } catch (error: SQLiteConstraintException) {
                    throw ValidationException("Já existe um ativo com este ticker nesta carteira.")
                }
                firstTransaction?.let { transaction ->
                    if (transaction.assetId != asset.id) {
                        throw ValidationException("A compra inicial deve pertencer ao ativo que está sendo cadastrado.")
                    }
                    transactionDao.insert(transaction.toEntity())
                    synchronizeInvestmentCash(transaction, asset)
                }
            }
        }
    }

    override suspend fun updateAsset(asset: Asset) = withContext(ioDispatcher) {
        try {
            assetDao.update(
                id = asset.id,
                ticker = asset.ticker,
                name = asset.name,
                type = asset.type,
                cryptoId = asset.cryptoId,
                annualRate = asset.annualRate,
                maturityDate = asset.maturityDate,
            )
        } catch (error: SQLiteConstraintException) {
            throw ValidationException("Já existe um ativo com este ticker nesta carteira.")
        }
    }

    override suspend fun deleteAsset(id: String) = withContext(ioDispatcher) {
        database.withTransaction {
            transactionDao.getByAsset(id).forEach { transaction ->
                cashTransactionDao.deleteBySourceTransaction(transaction.id)
            }
            assetDao.delete(id)
        }
    }

    override fun observeTransactions(assetId: String): Flow<List<Transaction>> =
        transactionDao.observeByAsset(assetId).map { list -> list.map { it.toDomain() } }

    override fun observeWalletTransactions(walletId: String): Flow<List<Transaction>> =
        transactionDao.observeByWallet(walletId).map { list -> list.map { it.toDomain() } }

    override suspend fun getWalletTransactions(walletId: String): List<Transaction> =
        withContext(ioDispatcher) {
            transactionDao.getByWallet(walletId).map { it.toDomain() }
        }

    override suspend fun getTransactions(assetId: String): List<Transaction> =
        withContext(ioDispatcher) {
            transactionDao.getByAsset(assetId).map { it.toDomain() }
        }

    override suspend fun addTransaction(transaction: Transaction) = withContext(ioDispatcher) {
        database.withTransaction {
            val asset = assetDao.findById(transaction.assetId)?.toDomain()
                ?: throw ValidationException("O ativo informado não foi encontrado.")
            transactionDao.insert(transaction.toEntity())
            synchronizeInvestmentCash(transaction, asset)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction) = withContext(ioDispatcher) {
        database.withTransaction {
            val previous = transactionDao.findById(transaction.id) ?: return@withTransaction
            val persistedTransaction = transaction.copy(assetId = previous.assetId)
            transactionDao.update(
                id = persistedTransaction.id,
                type = persistedTransaction.type,
                quantity = persistedTransaction.quantity,
                unitPrice = persistedTransaction.unitPrice,
                fees = persistedTransaction.fees,
                dividendAmount = persistedTransaction.dividendAmount,
                date = persistedTransaction.date,
                notes = persistedTransaction.notes,
            )
            val asset = assetDao.findById(previous.assetId)?.toDomain()
                ?: throw ValidationException("O ativo vinculado à transação não foi encontrado.")
            synchronizeInvestmentCash(persistedTransaction, asset)
        }
    }

    override suspend fun deleteTransaction(id: String) = withContext(ioDispatcher) {
        database.withTransaction {
            cashTransactionDao.deleteBySourceTransaction(id)
            transactionDao.delete(id)
        }
    }

    override suspend fun countAssetsByType(walletId: String, type: AssetType): Int =
        withContext(ioDispatcher) {
            assetDao.getByWallet(walletId).count { it.type == type }
        }

    /**
     * Synchronizes cash for BUY, SELL and DIVIDEND records.
     * The caller runs this inside the same Room transaction as the investment write.
     */
    private suspend fun synchronizeInvestmentCash(transaction: Transaction, asset: Asset) {
        val linkedEntry = cashTransactionDao.findBySourceTransaction(transaction.id)
        val cashType = when (transaction.type) {
            TransactionType.BUY -> CashTransactionType.WITHDRAWAL
            TransactionType.SELL, TransactionType.DIVIDEND -> CashTransactionType.DEPOSIT
        }
        val netAmount = when (transaction.type) {
            TransactionType.BUY -> transaction.grossValue.add(transaction.fees)
            TransactionType.SELL, TransactionType.DIVIDEND ->
                transaction.grossValue.subtract(transaction.fees)
        }
        if (netAmount.signum() <= 0) {
            throw ValidationException(
                when (transaction.type) {
                    TransactionType.BUY -> "O valor total da compra deve ser maior que zero."
                    TransactionType.SELL -> "O valor líquido da venda deve ser maior que zero."
                    TransactionType.DIVIDEND -> "O valor líquido do rendimento deve ser maior que zero."
                },
            )
        }
        val label = when (transaction.type) {
            TransactionType.BUY -> "Compra"
            TransactionType.SELL -> "Venda"
            TransactionType.DIVIDEND -> "Rendimento"
        }
        val cashMovement = CashTransaction(
            id = linkedEntry?.id ?: UUID.randomUUID().toString(),
            walletId = asset.walletId,
            type = cashType,
            amount = netAmount,
            date = transaction.date,
            description = "$label de ${asset.ticker}",
            createdAt = linkedEntry?.createdAt ?: transaction.createdAt,
            sourceTransactionId = transaction.id,
        )
        val currentWalletEntries = cashTransactionDao.getByWallet(asset.walletId)
            .filterNot { it.sourceTransactionId == transaction.id }
            .map { it.toDomain() }
        validateChronologicalCashBalance(currentWalletEntries + cashMovement)
        if (linkedEntry != null) cashTransactionDao.deleteBySourceTransaction(transaction.id)
        cashTransactionDao.insert(cashMovement.toEntity())
    }

    private fun validateChronologicalCashBalance(entries: List<CashTransaction>) {
        var runningBalance = BigDecimal.ZERO
        var openingBalanceCount = 0

        entries.sortedWith(
            compareBy<CashTransaction> { it.date }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        ).forEach { entry ->
            if (entry.type == CashTransactionType.OPENING_BALANCE) {
                openingBalanceCount += 1
                if (openingBalanceCount > 1) {
                    throw ValidationException("O saldo inicial desta carteira já foi cadastrado.")
                }
            }

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
    }
}
