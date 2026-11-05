package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.dao.AssetDao
import br.com.carteirainvest.data.local.dao.QuoteDao
import br.com.carteirainvest.data.local.dao.SnapshotDao
import br.com.carteirainvest.data.local.dao.TransactionDao
import br.com.carteirainvest.data.local.entity.PortfolioSnapshotEntity
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.PortfolioSnapshot
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.usecase.PortfolioCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepositoryImpl @Inject constructor(
    private val assetDao: AssetDao,
    private val transactionDao: TransactionDao,
    private val quoteDao: QuoteDao,
    private val snapshotDao: SnapshotDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PortfolioRepository {

    override fun observePortfolio(walletId: String): Flow<PortfolioSummary> =
        combine(
            assetDao.observeByWallet(walletId),
            transactionDao.observeByWallet(walletId),
            quoteDao.observeAll(),
        ) { assets, transactions, quotes ->
            PortfolioCalculator.buildSummary(
                walletId = walletId,
                assets = assets.map { it.toDomain() },
                transactions = transactions.map { it.toDomain() },
                quotes = quotes.map { it.toDomain() },
                includeClosed = false,
            )
        }.flowOn(ioDispatcher)

    override fun observeAssetPositions(walletId: String): Flow<List<PortfolioPosition>> =
        combine(
            assetDao.observeByWallet(walletId),
            transactionDao.observeByWallet(walletId),
            quoteDao.observeAll(),
        ) { assets, transactions, quotes ->
            PortfolioCalculator.buildSummary(
                walletId = walletId,
                assets = assets.map { it.toDomain() },
                transactions = transactions.map { it.toDomain() },
                quotes = quotes.map { it.toDomain() },
                includeClosed = true,
            ).positions
        }.flowOn(ioDispatcher)

    override fun observeSnapshots(walletId: String): Flow<List<PortfolioSnapshot>> =
        snapshotDao.observeByWallet(walletId)
            .map { snapshots -> snapshots.map { it.toDomain() } }

    override suspend fun getPortfolio(walletId: String): PortfolioSummary =
        withContext(ioDispatcher) {
            PortfolioCalculator.buildSummary(
                walletId = walletId,
                assets = assetDao.getByWallet(walletId).map { it.toDomain() },
                transactions = transactionDao.getByWallet(walletId).map { it.toDomain() },
                quotes = quoteDao.getAll().map { it.toDomain() },
                includeClosed = false,
            )
        }

    override suspend fun captureSnapshot(walletId: String) = withContext(ioDispatcher) {
        val portfolio = getPortfolio(walletId)
        if (
            portfolio.totalValue.compareTo(BigDecimal.ZERO) == 0 &&
            portfolio.totalInvested.compareTo(BigDecimal.ZERO) == 0
        ) {
            return@withContext
        }
        val today = LocalDate.now()
        snapshotDao.upsert(
            PortfolioSnapshotEntity(
                walletId = walletId,
                date = today,
                value = portfolio.totalValue,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
