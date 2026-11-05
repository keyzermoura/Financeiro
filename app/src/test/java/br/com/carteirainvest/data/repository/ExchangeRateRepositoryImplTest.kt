package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.CacheTtl
import br.com.carteirainvest.data.remote.common.NetworkRetryPolicy
import br.com.carteirainvest.data.remote.common.RetryConfig
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.testutil.FakeExchangeRateDao
import br.com.carteirainvest.testutil.RecordingSleeper
import br.com.carteirainvest.testutil.TestTimeProvider
import br.com.carteirainvest.testutil.awesomeApi
import br.com.carteirainvest.testutil.exchangeRateEntity
import kotlinx.coroutines.Dispatchers
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

class ExchangeRateRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeExchangeRateDao
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var repository: ExchangeRateRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = FakeExchangeRateDao()
        timeProvider = TestTimeProvider()
        repository = ExchangeRateRepositoryImpl(
            awesomeApi = awesomeApi(server),
            exchangeRateDao = dao,
            timeProvider = timeProvider,
            apiCallExecutor = NetworkRetryPolicy(
                sleeper = RecordingSleeper(),
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
    fun `fetches USD and EUR and stores them`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "USDBRL": {"name":"Dólar/Real","bid":"5.4321","ask":"5.4330","pctChange":"0.25","timestamp":"1700000000"},
                  "EURBRL": {"name":"Euro/Real","bid":"6.1000","ask":"6.1100","pctChange":"-0.10","timestamp":"1700000000"}
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refresh(force = true)

        assertTrue(result is Resource.Success)
        assertFalse(result.isStale)
        assertEquals(2, result.dataOrNull()!!.size)
        assertEquals(2, dao.snapshot().size)
        assertEquals(BigDecimal("5.4321"), dao.snapshot().first { it.pair == "USD-BRL" }.bid)
    }

    @Test
    fun `uses the 1 hour TTL and skips the network while fresh`() = runTest {
        dao.upsertAll(
            listOf(
                exchangeRateEntity(pair = "USD-BRL", updatedAt = timeProvider.now),
                exchangeRateEntity(pair = "EUR-BRL", updatedAt = timeProvider.now),
            ),
        )

        val result = repository.refresh(force = false)

        assertTrue(result is Resource.Success)
        assertEquals(2, result.dataOrNull()!!.size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `returns stale cached rates when the provider returns 500`() = runTest {
        val staleTimestamp = timeProvider.now - CacheTtl.FX_MILLIS - 1
        dao.upsertAll(listOf(exchangeRateEntity(updatedAt = staleTimestamp)))
        repeat(2) { server.enqueue(MockResponse().setResponseCode(500)) }

        val result = repository.refresh(force = true)

        assertTrue(result is Resource.Success)
        assertTrue(result.isStale)
        assertEquals(staleTimestamp, result.dataOrNull()!!.single().updatedAt)
        assertTrue(result.dataOrNull()!!.single().isStale)
        assertTrue(repository.observeRates().first().single().isStale)
    }

    @Test
    fun `returns typed failure without cache`() = runTest {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(500)) }

        val result = repository.refresh(force = true)

        assertTrue(result is Resource.Failure)
        assertEquals(0, dao.snapshot().size)
    }
}
