package br.com.carteirainvest.data.sync

import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.RefreshSummary
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.ExchangeRateRepository
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.repository.QuoteRepository
import br.com.carteirainvest.domain.usecase.alert.CheckAlertsUseCase
import br.com.carteirainvest.domain.model.AssetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a background sync. [Stale] means the refresh failed but Room had a previous
 * value; the Worker should retry later and only notify the user after repeated failures.
 */
sealed interface SyncOutcome {
    data class Success(val updated: Int = 0) : SyncOutcome
    data class Stale(val error: DataError) : SyncOutcome
    data class Failure(val error: DataError) : SyncOutcome
}

/**
 * Orchestrates the three independent data types. Workers stay thin and call exactly the
 * same repository methods used by pull-to-refresh, avoiding duplicated business logic.
 */
interface MarketSynchronizer {
    suspend fun syncB3(force: Boolean = false): SyncOutcome
    suspend fun syncCrypto(force: Boolean = false): SyncOutcome
    suspend fun syncFx(force: Boolean = false): SyncOutcome
}

@Singleton
class DefaultMarketSynchronizer @Inject constructor(
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val portfolioRepository: PortfolioRepository,
    private val checkAlerts: CheckAlertsUseCase,
) : MarketSynchronizer {

    override suspend fun syncB3(force: Boolean): SyncOutcome =
        refreshQuotes(B3_TYPES, force)

    override suspend fun syncCrypto(force: Boolean): SyncOutcome =
        refreshQuotes(setOf(AssetType.CRYPTO), force)

    override suspend fun syncFx(force: Boolean): SyncOutcome =
        when (val result = exchangeRateRepository.refresh(force)) {
            is Resource.Success -> {
                if (result.isStale) {
                    SyncOutcome.Stale(DataError.Unknown("Câmbio exibido do cache."))
                } else {
                    SyncOutcome.Success(result.data.size)
                }
            }

            is Resource.Failure -> SyncOutcome.Failure(result.error)
        }

    private suspend fun refreshQuotes(
        types: Set<AssetType>,
        force: Boolean,
    ): SyncOutcome {
        val assets = assetRepository.getAllAssets().filter { it.type in types }
        if (assets.isEmpty()) return SyncOutcome.Success()

        return when (val result = quoteRepository.refreshQuotes(assets, force)) {
            is Resource.Success -> {
                assets.map { it.walletId }.distinct().forEach { walletId ->
                    runCatching { portfolioRepository.captureSnapshot(walletId) }
                }
                runCatching { checkAlerts() }
                if (result.isStale) {
                    SyncOutcome.Stale(staleError(result.data))
                } else {
                    SyncOutcome.Success(result.data.updated)
                }
            }

            is Resource.Failure -> SyncOutcome.Failure(result.error)
        }
    }

    private fun staleError(summary: RefreshSummary): DataError =
        DataError.Unknown(summary.messages.firstOrNull() ?: "Dados exibidos do cache.")

    private companion object {
        val B3_TYPES = setOf(AssetType.STOCK, AssetType.FII, AssetType.ETF)
    }
}
