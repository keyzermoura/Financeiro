package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.CacheTtl
import br.com.carteirainvest.data.remote.common.NetworkRetryPolicy
import br.com.carteirainvest.data.remote.common.RetryConfig
import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.testutil.FakeQuoteDao
import br.com.carteirainvest.testutil.RecordingSleeper
import br.com.carteirainvest.testutil.TestTimeProvider
import br.com.carteirainvest.testutil.brapiApi
import br.com.carteirainvest.testutil.stockAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Explicit HTTP 429 test: validates that the repository respects Retry-After,
 * applies exponential backoff, and returns stale cached data when the provider
 * keeps rate-limiting.
 */
class QuoteRepositoryRateLimitTest {

    private lateinit var server: MockWebServer
    private lateinit var quoteDao: FakeQuoteDao
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var sleeper: RecordingSleeper
    private lateinit var repository: br.com.carteirainvest.data.repository.QuoteRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        quoteDao = FakeQuoteDao()
        timeProvider = TestTimeProvider()
        sleeper = RecordingSleeper()
        repository = br.com.carteirainvest.data.repository.QuoteRepositoryImpl(
            brapiApi = brapiApi(server),
            coinGeckoApi = br.com.carteirainvest.testutil.coinGeckoApi(server),
            quoteDao = quoteDao,
            remoteDividendDao = br.com.carteirainvest.testutil.FakeRemoteDividendDao(),
            timeProvider = timeProvider,
            apiCallExecutor = NetworkRetryPolicy(
                sleeper = sleeper,
                config = RetryConfig(
                    maxAttempts = 3,
                    baseDelayMillis = 10,
                    maxDelayMillis = 100,
                    jitterRatio = 0.0,
                ),
            ),
            ioDispatcher = Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `HTTP 429 with Retry-After applies backoff and returns stale cache`() = runTest {
        // Seed the cache with stale data
        val staleTimestamp = timeProvider.now - CacheTtl.EQUITY_MILLIS - 1
        quoteDao.upsertAll(listOf(br.com.carteirainvest.testutil.quoteEntity(
            ticker = "PETR4",
            updatedAt = staleTimestamp,
        )))

        // All responses are HTTP 429 with Retry-After: 2 seconds
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "2")
                    .setBody("""{"error":true,"message":"rate limit"}"""),
            )
        }

        val result = repository.refreshQuotes(listOf(stockAsset()), force = true)

        assertTrue(result is Resource.Success)
        assertTrue(result.isStale)
        assertEquals(1, result.dataOrNull()!!.failures)

        // 3 attempts = 2 sleeps between them, each honouring Retry-After (2000 ms)
        assertEquals(listOf(2000L, 2000L), sleeper.delays)
        assertEquals(3, server.requestCount)

        // The observed flow should emit the stale cached quote
        val observed = repository.observeQuotes().first().single()
        assertTrue(observed.isStale)
        assertEquals(staleTimestamp, observed.updatedAt)
    }

    @Test
    fun `HTTP 429 without Retry-After uses exponential backoff`() = runTest {
        quoteDao.upsertAll(listOf(br.com.carteirainvest.testutil.quoteEntity(
            ticker = "PETR4",
            updatedAt = timeProvider.now - CacheTtl.EQUITY_MILLIS - 1,
        )))

        // No Retry-After header - exponential backoff: 10ms, 20ms
        repeat(3) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody("""{"error":true}"""),
            )
        }

        val result = repository.refreshQuotes(listOf(stockAsset()), force = true)

        assertTrue(result is Resource.Success)
        assertTrue(result.isStale)
        // Base delay 10ms, then 20ms (exponential, no jitter in test)
        assertEquals(listOf(10L, 20L), sleeper.delays)
    }
}
