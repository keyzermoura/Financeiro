package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.CacheTtl
import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.core.util.TimeProvider
import br.com.carteirainvest.data.local.dao.ExchangeRateDao
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.remote.awesomeapi.AwesomeApi
import br.com.carteirainvest.data.remote.awesomeapi.toEntity
import br.com.carteirainvest.data.remote.common.ApiCallExecutor
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val awesomeApi: AwesomeApi,
    private val exchangeRateDao: ExchangeRateDao,
    private val timeProvider: TimeProvider,
    private val apiCallExecutor: ApiCallExecutor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExchangeRateRepository {

    /** FX has only two pairs, so one gate is enough for single-flight. */
    private val requestGate = Mutex()

    override fun observeRates(): Flow<List<ExchangeRate>> =
        exchangeRateDao.observeAll().map { list ->
            list.toDomainList(timeProvider.nowMillis())
        }

    override suspend fun getRates(): List<ExchangeRate> = withContext(ioDispatcher) {
        exchangeRateDao.getAll().toDomainList(timeProvider.nowMillis())
    }

    override suspend fun refresh(force: Boolean): Resource<List<ExchangeRate>> =
        withContext(ioDispatcher) {
            requestGate.withLock {
                val cached = exchangeRateDao.getAll()
                val now = timeProvider.nowMillis()
                val allFresh = cached.isNotEmpty() &&
                    cached.all { CacheTtl.isFresh(it.updatedAt, now, CacheTtl.FX_MILLIS) }
                if (!force && allFresh) {
                    return@withLock Resource.Success(cached.toDomainList(now))
                }

                val response = apiCallExecutor.execute {
                    awesomeApi.getLast(PAIRS.joinToString(","))
                }
                when (response) {
                    is Resource.Success -> {
                        val entities = PAIRS.mapNotNull { pair ->
                            val key = pair.replace("-", "")
                            response.data[key]?.toEntity(pair, now)
                        }
                        if (entities.isNotEmpty()) exchangeRateDao.upsertAll(entities)
                        val stored = exchangeRateDao.getAll()
                        if (stored.isEmpty()) {
                            Resource.Failure(
                                br.com.carteirainvest.domain.model.DataError.Parsing(
                                    "A AwesomeAPI não retornou taxas válidas.",
                                ),
                            )
                        } else {
                            Resource.Success(stored.toDomainList(now))
                        }
                    }

                    is Resource.Failure -> {
                        if (cached.isNotEmpty()) {
                            Resource.Success(
                                data = cached.map { it.toDomain(isStale = true) },
                                isStale = true,
                            )
                        } else {
                            response
                        }
                    }
                }
            }
        }

    private fun List<ExchangeRateEntity>.toDomainList(nowMillis: Long): List<ExchangeRate> =
        map { entity ->
            entity.toDomain(
                isStale = CacheTtl.isStale(entity.updatedAt, nowMillis, CacheTtl.FX_MILLIS),
            )
        }

    private companion object {
        val PAIRS = listOf("USD-BRL", "EUR-BRL")
    }
}
