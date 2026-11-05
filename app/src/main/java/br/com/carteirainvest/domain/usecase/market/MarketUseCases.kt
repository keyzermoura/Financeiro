package br.com.carteirainvest.domain.usecase.market

import br.com.carteirainvest.domain.model.BenchmarkPoint
import br.com.carteirainvest.domain.model.RefreshSummary
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RefreshWalletQuotesUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
    private val portfolioRepository: PortfolioRepository,
) {
    suspend operator fun invoke(walletId: String, force: Boolean = false): Resource<RefreshSummary> {
        val assets = assetRepository.getAssets(walletId)
        if (assets.isEmpty()) return Resource.Success(RefreshSummary.Empty)
        val result = quoteRepository.refreshQuotes(assets, force)
        if (result is Resource.Success) {
            portfolioRepository.captureSnapshot(walletId)
        }
        return result
    }
}

class RefreshAssetQuotesUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
    private val portfolioRepository: PortfolioRepository,
) {
    suspend operator fun invoke(assetId: String, force: Boolean = true): Resource<RefreshSummary> {
        val asset = assetRepository.getAsset(assetId) ?: return Resource.Success(RefreshSummary.Empty)
        val result = quoteRepository.refreshQuotes(listOf(asset), force)
        if (result is Resource.Success) {
            portfolioRepository.captureSnapshot(asset.walletId)
        }
        return result
    }
}

class RefreshAllQuotesUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Resource<RefreshSummary> {
        val assets = assetRepository.getAllAssets()
        return quoteRepository.refreshQuotes(assets, force)
    }
}

class FetchRemoteDividendsUseCase @Inject constructor(
    private val quoteRepository: QuoteRepository,
) {
    suspend operator fun invoke(
        ticker: String,
        force: Boolean = false,
    ): Resource<List<RemoteDividend>> = quoteRepository.refreshRemoteDividends(ticker, force)
}

class ObserveRemoteDividendsUseCase @Inject constructor(
    private val quoteRepository: QuoteRepository,
) {
    operator fun invoke(ticker: String): Flow<List<RemoteDividend>> =
        quoteRepository.observeRemoteDividends(ticker)
}

class FetchBenchmarkUseCase @Inject constructor(
    private val quoteRepository: QuoteRepository,
) {
    suspend operator fun invoke(
        ticker: String = "BOVA11",
        range: String = "6mo",
    ): Resource<List<BenchmarkPoint>> = quoteRepository.fetchBenchmark(ticker, range)
}
