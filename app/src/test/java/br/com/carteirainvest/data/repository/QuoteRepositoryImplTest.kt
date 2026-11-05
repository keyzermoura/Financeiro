package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.CacheTtl
import br.com.carteirainvest.data.remote.common.NetworkRetryPolicy
import br.com.carteirainvest.data.remote.common.RetryConfig
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.testutil.FakeQuoteDao
import br.com.carteirainvest.testutil.FakeRemoteDividendDao
import br.com.carteirainvest.testutil.RecordingSleeper
import br.com.carteirainvest.testutil.TestTimeProvider
import br.com.carteirainvest.testutil.brapiApi
import br.com.carteirainvest.testutil.coinGeckoApi
import br.com.carteirainvest.testutil.cryptoAsset
import br.com.carteirainvest.testutil.quoteEntity
import br.com.carteirainvest.testutil.stockAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class QuoteRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var quoteDao: FakeQuoteDao
    private lateinit var dividendDao: FakeRemoteDividendDao
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var sleeper: RecordingSleeper
    private lateinit var repository: QuoteRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        quoteDao = FakeQuoteDao()
        dividendDao = FakeRemoteDividendDao()
        timeProvider = TestTimeProvider()
        sleeper = RecordingSleeper()
        repository = QuoteRepositoryImpl(
            brapiApi = brapiApi(server),
            coinGeckoApi = coinGeckoApi(server),
            quoteDao = quoteDao,
            remoteDividendDao = dividendDao,
            timeProvider = timeProvider,
            apiCallExecutor = NetworkRetryPolicy(
                sleeper = sleeper,
                config = RetryConfig(
                    maxAttempts = 2,
                    baseDelayMillis = 1,
                    maxDelayMillis = 2,
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
    fun `fetches B3 quotes and persists them in Room`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "results": [
                    {
                      "symbol": "PETR4",
                      "shortName": "Petrobras PN",
                      "regularMarketPrice": 38.50,
                      "regularMarketPreviousClose": 38.00,
                      "regularMarketChange": 0.50,
                      "regularMarketChangePercent": 1.3158
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshQuotes(listOf(stockAsset()), force = true)

        assertTrue(result is Resource.Success)
        val summary = result.dataOrNull()!!
        assertEquals(1, summary.updated)
        assertEquals(0, summary.failures)
        assertFalse(result.isStale)
        val stored = quoteDao.snapshot().single()
        assertEquals("EQUITY:PETR4", stored.quoteKey)
        assertEquals(BigDecimal("38.50"), stored.price)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not hit the network when the cache is fresh`() = runTest {
        quoteDao.upsertAll(listOf(quoteEntity(updatedAt = timeProvider.now)))

        val result = repository.refreshQuotes(listOf(stockAsset()), force = false)

        assertTrue(result is Resource.Success)
        assertEquals(1, result.dataOrNull()!!.fresh)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `returns stale cache and honours Retry-After after HTTP 429`() = runTest {
        val staleTimestamp = timeProvider.now - CacheTtl.EQUITY_MILLIS - 1
        quoteDao.upsertAll(listOf(quoteEntity(updatedAt = staleTimestamp)))
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "1")
                    .setBody("""{"error":true,"message":"rate limit"}"""),
            )
        }

        val result = repository.refreshQuotes(listOf(stockAsset()), force = true)

        assertTrue(result is Resource.Success)
        val summary = result.dataOrNull()!!
        assertTrue(result.isStale)
        assertEquals(1, summary.failures)
        assertEquals(listOf(1_000L), sleeper.delays)
        assertEquals(2, server.requestCount)

        val cached = repository.getCachedQuotes().single()
        assertTrue(cached.isStale)
        assertEquals(staleTimestamp, cached.updatedAt)

        val observed = repository.observeQuotes().first().single()
        assertTrue(observed.isStale)
    }

    @Test
    fun `fetches crypto quotes from CoinGecko and derives the key`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"bitcoin":{"brl":100.0,"brl_24h_change":5.0}}""",
            ),
        )

        val result = repository.refreshQuotes(listOf(cryptoAsset()), force = true)

        assertTrue(result is Resource.Success)
        assertEquals(1, result.dataOrNull()!!.updated)
        val stored = quoteDao.snapshot().single()
        assertEquals("CRYPTO:BTC", stored.quoteKey)
        assertEquals(BigDecimal("100.0"), stored.price)
        assertEquals(BigDecimal("5.00"), stored.changePercent)
    }

    @Test
    fun `concurrent refreshes for the same symbol make a single request`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(200, TimeUnit.MILLISECONDS)
                .setBody(
                    """{"results":[{"symbol":"PETR4","regularMarketPrice":38.50}]}""",
                ),
        )

        val first = async { repository.refreshQuotes(listOf(stockAsset()), force = true) }
        val second = async { repository.refreshQuotes(listOf(stockAsset()), force = false) }
        val results = listOf(first, second).awaitAll()

        assertTrue(results.all { it is Resource.Success })
        assertEquals(1, server.requestCount)
        assertEquals(1, quoteDao.snapshot().size)
    }

    @Test
    fun `loads announced dividends and maps them to the cache`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "results": [
                    {
                      "symbol": "PETR4",
                      "dividendsData": {
                        "cashDividends": [
                          {
                            "paymentDate": "2025-01-15",
                            "rate": 1.20,
                            "label": "DIVIDENDO"
                          }
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshRemoteDividends("PETR4", force = true)

        assertTrue(result is Resource.Success)
        val dividends = result.dataOrNull()!!
        assertEquals(1, dividends.size)
        assertEquals(BigDecimal("1.20"), dividends.single().ratePerShare)
    }

    @Test
    fun `returns typed failure when there is no cache and the provider fails`() = runTest {
        repeat(2) {
            server.enqueue(MockResponse().setResponseCode(503))
        }

        val result = repository.refreshQuotes(listOf(stockAsset()), force = true)

        assertTrue(result is Resource.Failure)
        assertEquals(0, quoteDao.snapshot().size)
    }
}
