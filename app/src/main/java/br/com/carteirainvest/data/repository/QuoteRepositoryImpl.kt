package br.com.carteirainvest.data.repository

import br.com.carteirainvest.BuildConfig
import br.com.carteirainvest.core.util.CacheTtl
import br.com.carteirainvest.core.util.CryptoCatalog
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.core.util.TimeProvider
import br.com.carteirainvest.data.local.dao.QuoteDao
import br.com.carteirainvest.data.local.dao.RemoteDividendDao
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.remote.brapi.BrapiApi
import br.com.carteirainvest.data.remote.brapi.toBenchmarkPoint
import br.com.carteirainvest.data.remote.brapi.toDividendEntities
import br.com.carteirainvest.data.remote.brapi.toQuoteEntity
import br.com.carteirainvest.data.remote.coingecko.CoinGeckoApi
import br.com.carteirainvest.data.remote.coingecko.toQuoteEntity
import br.com.carteirainvest.data.remote.common.ApiCallExecutor
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.BenchmarkPoint
import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.RefreshSummary
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.model.userMessage
import br.com.carteirainvest.domain.repository.QuoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepositoryImpl @Inject constructor(
    private val brapiApi: BrapiApi,
    private val coinGeckoApi: CoinGeckoApi,
    private val quoteDao: QuoteDao,
    private val remoteDividendDao: RemoteDividendDao,
    private val timeProvider: TimeProvider,
    private val apiCallExecutor: ApiCallExecutor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : QuoteRepository {

    /**
     * Brapi's free tier allows one concurrent request. Every Brapi call goes through this
     * gate, including dividends and benchmark history requests.
     */
    private val brapiGate = Mutex()

    /** CoinGecko's public limit is shared by IP; serializing reduces 429 bursts. */
    private val coinGeckoGate = Mutex()

    /**
     * Per-symbol single-flight. Concurrent refreshes for the same ticker queue here and
     * the second one re-checks the cache after acquiring the lock, so only one network
     * request is made. Locks are keyed by the stable quote key (`EQUITY:PETR4`).
     */
    private val symbolLocks = ConcurrentHashMap<String, Mutex>()

    override fun observeQuotes(): Flow<List<Quote>> =
        quoteDao.observeAll().map { entities -> entities.toDomainList(timeProvider.nowMillis()) }

    override suspend fun getCachedQuotes(): List<Quote> = withContext(ioDispatcher) {
        quoteDao.getAll().toDomainList(timeProvider.nowMillis())
    }

    override suspend fun refreshQuotes(
        assets: List<Asset>,
        force: Boolean,
    ): Resource<RefreshSummary> = withContext(ioDispatcher) {
        val targets = assets
            .filter { it.supportsMarketQuote }
            .distinctBy { it.quoteKey }
        if (targets.isEmpty()) return@withContext Resource.Success(RefreshSummary.Empty)

        val keys = targets.mapNotNull { it.quoteKey }.distinct().sorted()
        withSymbolLocks(keys) {
            refreshQuotesLocked(targets, force)
        }
    }

    private suspend fun refreshQuotesLocked(
        targets: List<Asset>,
        force: Boolean,
    ): Resource<RefreshSummary> {
        val now = timeProvider.nowMillis()
        val cached = quoteDao.getByKeys(targets.mapNotNull { it.quoteKey }).associateBy { it.quoteKey }
        val staleTargets = if (force) {
            targets
        } else {
            targets.filter { asset ->
                val quote = cached[asset.quoteKey]
                quote == null || CacheTtl.isStale(quote.updatedAt, now, ttlFor(asset.type))
            }
        }
        val fresh = targets.size - staleTargets.size
        if (staleTargets.isEmpty()) {
            return Resource.Success(RefreshSummary(fresh = fresh))
        }

        var updated = 0
        var failures = 0
        var staleFallback = false
        var firstError: DataError? = null
        val messages = mutableListOf<String>()

        val equityAssets = staleTargets
            .filter { it.type != AssetType.CRYPTO }
            .distinctBy { it.ticker.uppercase() }
        val cryptoAssets = staleTargets
            .filter { it.type == AssetType.CRYPTO }
            .distinctBy { it.ticker.uppercase() }

        if (equityAssets.isNotEmpty()) {
            val tickers = equityAssets.map { it.ticker.uppercase() }
            val response = brapiGate.withLock {
                apiCallExecutor.execute {
                    brapiApi.getQuotes(
                        tickers = tickers.joinToString(","),
                        token = BuildConfig.BRAPI_TOKEN.takeIf { it.isNotBlank() },
                        dividends = false,
                    )
                }
            }
            when (response) {
                is Resource.Success -> {
                    val body = response.data
                    if (body.error == true) {
                        failures += equityAssets.size
                        messages += body.message ?: "A Brapi não retornou as cotações."
                        if (equityAssets.any { cached[it.quoteKey] != null }) staleFallback = true
                    } else {
                        val bySymbol = body.results.orEmpty()
                            .mapNotNull { dto -> dto.symbol?.uppercase()?.let { it to dto } }
                            .toMap()
                        val entities = equityAssets.mapNotNull { asset ->
                            bySymbol[asset.ticker.uppercase()]?.toQuoteEntity(asset, now)
                        }
                        if (entities.isNotEmpty()) {
                            quoteDao.upsertAll(entities)
                            updated += entities.size
                        }
                        val missing = tickers.size - entities.size
                        if (missing > 0) {
                            failures += missing
                            messages += "Não foi possível obter cotação para $missing ativo(s) da B3."
                            if (equityAssets.any { cached[it.quoteKey] != null }) staleFallback = true
                        }
                    }
                }

                is Resource.Failure -> {
                    failures += equityAssets.size
                    firstError = firstError ?: response.error
                    messages += response.error.userMessage("atualizar ações e FIIs")
                    if (equityAssets.any { cached[it.quoteKey] != null }) staleFallback = true
                }
            }
        }

        if (cryptoAssets.isNotEmpty()) {
            val requests = cryptoAssets.mapNotNull { asset ->
                val id = CryptoCatalog.resolveId(asset.ticker, asset.cryptoId)
                if (id == null) {
                    failures += 1
                    messages += "Informe o ID CoinGecko para ${asset.ticker}."
                    null
                } else {
                    asset to id
                }
            }
            if (requests.isNotEmpty()) {
                val ids = requests.map { it.second }.distinct()
                val response = coinGeckoGate.withLock {
                    apiCallExecutor.execute {
                        coinGeckoApi.getSimplePrice(ids.joinToString(","))
                    }
                }
                when (response) {
                    is Resource.Success -> {
                        val prices = response.data
                        val entities = requests.mapNotNull { (asset, id) ->
                            prices[id]?.toQuoteEntity(asset, now)
                        }
                        if (entities.isNotEmpty()) {
                            quoteDao.upsertAll(entities)
                            updated += entities.size
                        }
                        val missing = requests.size - entities.size
                        if (missing > 0) {
                            failures += missing
                            messages += "A CoinGecko não retornou preço para $missing criptoativo(s)."
                            if (requests.any { (asset, _) -> cached[asset.quoteKey] != null }) {
                                staleFallback = true
                            }
                        }
                    }

                    is Resource.Failure -> {
                        failures += requests.size
                        firstError = firstError ?: response.error
                        messages += response.error.userMessage("atualizar criptomoedas")
                        if (requests.any { (asset, _) -> cached[asset.quoteKey] != null }) {
                            staleFallback = true
                        }
                    }
                }
            }
        }

        val hasCachedValue = targets.any { cached[it.quoteKey] != null }
        if (updated == 0 && failures > 0 && fresh == 0 && !hasCachedValue) {
            return Resource.Failure(
                firstError ?: DataError.Unknown("Não foi possível atualizar as cotações."),
            )
        }

        return Resource.Success(
            data = RefreshSummary(
                updated = updated,
                fresh = fresh,
                failures = failures,
                messages = messages.distinct(),
            ),
            isStale = staleFallback,
        )
    }

    override fun observeRemoteDividends(ticker: String): Flow<List<RemoteDividend>> =
        remoteDividendDao.observeByTicker(ticker.uppercase())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun refreshRemoteDividends(
        ticker: String,
        force: Boolean,
    ): Resource<List<RemoteDividend>> = withContext(ioDispatcher) {
        val normalized = ticker.trim().uppercase()
        val cached = remoteDividendDao.getByTicker(normalized)
        val now = timeProvider.nowMillis()
        val fresh = cached.isNotEmpty() &&
            CacheTtl.isFresh(
                cached.maxOfOrNull { it.updatedAt } ?: 0L,
                now,
                CacheTtl.DIVIDEND_MILLIS,
            )
        if (!force && fresh) {
            return@withContext Resource.Success(cached.map { it.toDomain() })
        }

        val response = brapiGate.withLock {
            apiCallExecutor.execute {
                brapiApi.getQuotes(
                    tickers = normalized,
                    token = BuildConfig.BRAPI_TOKEN.takeIf { it.isNotBlank() },
                    dividends = true,
                )
            }
        }
        return@withContext when (response) {
            is Resource.Success -> {
                val body = response.data
                if (body.error == true) {
                    fallbackDividends(cached, DataError.Unknown(body.message))
                } else {
                    val dto = body.results
                        ?.firstOrNull { it.symbol?.equals(normalized, ignoreCase = true) == true }
                        ?: body.results?.firstOrNull()
                    val entities = dto?.toDividendEntities(normalized, now).orEmpty()
                    if (entities.isNotEmpty()) remoteDividendDao.upsertAll(entities)
                    Resource.Success(remoteDividendDao.getByTicker(normalized).map { it.toDomain() })
                }
            }

            is Resource.Failure -> fallbackDividends(cached, response.error)
        }
    }

    private fun fallbackDividends(
        cached: List<RemoteDividendEntity>,
        error: DataError,
    ): Resource<List<RemoteDividend>> =
        if (cached.isNotEmpty()) {
            Resource.Success(cached.map { it.toDomain() }, isStale = true)
        } else {
            Resource.Failure(error)
        }

    override suspend fun fetchBenchmark(
        ticker: String,
        range: String,
    ): Resource<List<BenchmarkPoint>> = withContext(ioDispatcher) {
        val response = brapiGate.withLock {
            apiCallExecutor.execute {
                brapiApi.getQuotes(
                    tickers = ticker,
                    token = BuildConfig.BRAPI_TOKEN.takeIf { it.isNotBlank() },
                    range = range,
                    interval = "1d",
                )
            }
        }
        when (response) {
            is Resource.Success -> {
                val body = response.data
                if (body.error == true) {
                    Resource.Failure(
                        DataError.Unknown(body.message ?: "Dados históricos indisponíveis."),
                    )
                } else {
                    val dto = body.results?.firstOrNull()
                    if (dto == null) {
                        Resource.Failure(DataError.Parsing("O benchmark não retornou dados."))
                    } else {
                        val points = dto.historicalDataPrice.orEmpty()
                            .mapNotNull { it.toBenchmarkPoint(BENCHMARK_ZONE) }
                            .distinctBy { it.date }
                            .sortedBy { it.date }
                        if (points.isEmpty()) {
                            Resource.Failure(DataError.Parsing("O benchmark não retornou dados."))
                        } else {
                            Resource.Success(points)
                        }
                    }
                }
            }

            is Resource.Failure -> response
        }
    }

    private fun List<QuoteEntity>.toDomainList(nowMillis: Long): List<Quote> = map { entity ->
        entity.toDomain(
            isStale = CacheTtl.isStale(
                updatedAt = entity.updatedAt,
                nowMillis = nowMillis,
                ttlMillis = CacheTtl.ttlFor(entity.source),
            ),
        )
    }

    private fun ttlFor(type: AssetType): Long = when (type) {
        AssetType.CRYPTO -> CacheTtl.CRYPTO_MILLIS
        else -> CacheTtl.EQUITY_MILLIS
    }

    /**
     * Acquires the per-symbol locks in a deterministic order to avoid deadlocks when
     * two refreshes overlap with different symbol sets.
     */
    private suspend fun <T> withSymbolLocks(keys: List<String>, block: suspend () -> T): T {
        val locks = keys.distinct().sorted().map { key ->
            symbolLocks.getOrPut(key) { Mutex() }
        }

        suspend fun acquire(index: Int): T {
            if (index == locks.size) return block()
            return locks[index].withLock { acquire(index + 1) }
        }
        return acquire(0)
    }

    private companion object {
        val BENCHMARK_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
