package br.com.carteirainvest.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.carteirainvest.data.local.AppDatabase
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.testutil.exchangeRateEntity
import br.com.carteirainvest.testutil.quoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteDaoInMemoryTest {

    private lateinit var database: AppDatabase
    private lateinit var quoteDao: QuoteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        quoteDao = database.quoteDao()
    }

    @Test
    fun upsertsAndRetrievesQuotes() = runBlocking {
        val entity = quoteEntity(ticker = "PETR4", price = "38.50")

        quoteDao.upsertAll(listOf(entity))

        val all = quoteDao.getAll()
        assertEquals(1, all.size)
        assertEquals("PETR4", all.single().ticker)
        assertEquals(BigDecimal("38.50"), all.single().price)
        assertEquals(QuoteSource.EQUITY, all.single().source)
    }

    @Test
    fun retrievesQuotesByKeys() = runBlocking {
        val petr4 = quoteEntity(ticker = "PETR4", price = "38.50")
        val vale3 = quoteEntity(ticker = "VALE3", price = "70.10")

        quoteDao.upsertAll(listOf(petr4, vale3))

        val found = quoteDao.getByKeys(listOf("EQUITY:PETR4"))
        assertEquals(1, found.size)
        assertEquals("EQUITY:PETR4", found.single().quoteKey)

        val notFound = quoteDao.getByKeys(listOf("EQUITY:XYZ"))
        assertEquals(0, notFound.size)
    }

    @Test
    fun upsertReplacesExistingQuote() = runBlocking {
        val original = quoteEntity(ticker = "PETR4", price = "38.50", updatedAt = 100L)
        val updated = quoteEntity(ticker = "PETR4", price = "39.00", updatedAt = 200L)

        quoteDao.upsertAll(listOf(original))
        quoteDao.upsertAll(listOf(updated))

        val all = quoteDao.getAll()
        assertEquals(1, all.size)
        assertEquals(BigDecimal("39.00"), all.single().price)
        assertEquals(200L, all.single().updatedAt)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExchangeRateDaoInMemoryTest {

    private lateinit var database: AppDatabase
    private lateinit var exchangeRateDao: ExchangeRateDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        exchangeRateDao = database.exchangeRateDao()
    }

    @Test
    fun upsertsAndRetrievesExchangeRates() = runBlocking {
        val usd = exchangeRateEntity(pair = "USD-BRL", updatedAt = 100L)
        val eur = exchangeRateEntity(pair = "EUR-BRL", updatedAt = 200L)

        exchangeRateDao.upsertAll(listOf(usd, eur))

        val all = exchangeRateDao.getAll()
        assertEquals(2, all.size)
        assertEquals("USD-BRL", all.first { it.pair == "USD-BRL" }.pair)
        assertEquals(BigDecimal("5.4321"), all.first { it.pair == "USD-BRL" }.bid)
    }

    @Test
    fun upsertReplacesExistingRate() = runBlocking {
        val original = exchangeRateEntity(pair = "USD-BRL", updatedAt = 100L)
        val updated = exchangeRateEntity(pair = "USD-BRL", updatedAt = 200L)

        exchangeRateDao.upsertAll(listOf(original))
        exchangeRateDao.upsertAll(listOf(updated))

        val all = exchangeRateDao.getAll()
        assertEquals(1, all.size)
        assertEquals(200L, all.single().updatedAt)
    }
}
