package br.com.carteirainvest.domain.repository

import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.BenchmarkPoint
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.PortfolioSnapshot
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.RefreshSummary
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.ReportFile
import br.com.carteirainvest.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeQuotes(): Flow<List<Quote>>
    suspend fun getCachedQuotes(): List<Quote>

    /**
     * Network-first refresh with Room fallback. `Success.isStale` means at least one
     * target could not be refreshed and its cached value will be shown.
     */
    suspend fun refreshQuotes(assets: List<Asset>, force: Boolean = false): Resource<RefreshSummary>

    fun observeRemoteDividends(ticker: String): Flow<List<RemoteDividend>>
    suspend fun refreshRemoteDividends(
        ticker: String,
        force: Boolean = false,
    ): Resource<List<RemoteDividend>>

    suspend fun fetchBenchmark(ticker: String, range: String): Resource<List<BenchmarkPoint>>
}

interface PortfolioRepository {
    fun observePortfolio(walletId: String): Flow<PortfolioSummary>
    fun observeAssetPositions(walletId: String): Flow<List<PortfolioPosition>>
    fun observeSnapshots(walletId: String): Flow<List<PortfolioSnapshot>>
    suspend fun getPortfolio(walletId: String): PortfolioSummary
    suspend fun captureSnapshot(walletId: String)
}

interface AlertRepository {
    fun observeAlerts(userId: String): Flow<List<AlertRule>>
    suspend fun getAlerts(userId: String): List<AlertRule>
    suspend fun getEnabledAlerts(): List<AlertRule>
    suspend fun saveAlert(alert: AlertRule)
    suspend fun deleteAlert(id: String)
    suspend fun markTriggered(id: String, timestamp: Long)
}

interface ExchangeRateRepository {
    fun observeRates(): Flow<List<ExchangeRate>>
    suspend fun getRates(): List<ExchangeRate>

    /** Network-first with a 1-hour TTL and typed stale fallback. */
    suspend fun refresh(force: Boolean = false): Resource<List<ExchangeRate>>
}

interface PortfolioReportExporter {
    suspend fun export(walletId: String): ReportFile
}

/** Android-free abstraction so the alert use case remains unit-testable. */
interface AlertNotifier {
    fun notifyPriceChange(
        alert: AlertRule,
        asset: Asset,
        price: Quote,
        changePercent: java.math.BigDecimal,
    ): Boolean

    fun notifyMaturity(alert: AlertRule, asset: Asset, daysLeft: Long): Boolean
}
