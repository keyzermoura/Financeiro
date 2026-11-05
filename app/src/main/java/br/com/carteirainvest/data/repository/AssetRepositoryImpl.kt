package br.com.carteirainvest.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.AppDatabase
import br.com.carteirainvest.data.local.dao.AssetDao
import br.com.carteirainvest.data.local.dao.TransactionDao
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.local.toEntity
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.repository.AssetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val assetDao: AssetDao,
    private val transactionDao: TransactionDao,
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
            try {
                database.withTransaction {
                    assetDao.insert(asset.toEntity())
                    firstTransaction?.let { transactionDao.insert(it.toEntity()) }
                }
            } catch (error: SQLiteConstraintException) {
                throw ValidationException("Já existe um ativo com este ticker nesta carteira.")
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
        assetDao.delete(id)
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
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun updateTransaction(transaction: Transaction) = withContext(ioDispatcher) {
        transactionDao.update(
            id = transaction.id,
            type = transaction.type,
            quantity = transaction.quantity,
            unitPrice = transaction.unitPrice,
            fees = transaction.fees,
            dividendAmount = transaction.dividendAmount,
            date = transaction.date,
            notes = transaction.notes,
        )
    }

    override suspend fun deleteTransaction(id: String) = withContext(ioDispatcher) {
        transactionDao.delete(id)
    }

    override suspend fun countAssetsByType(walletId: String, type: AssetType): Int =
        withContext(ioDispatcher) {
            assetDao.getByWallet(walletId).count { it.type == type }
        }
}
