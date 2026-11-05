package br.com.carteirainvest.data.remote.brapi

import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.testutil.stockAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class BrapiMappersTest {

    @Test
    fun `maps a valid quote DTO to a Room entity`() {
        val dto = BrapiQuoteDto(
            symbol = "PETR4",
            shortName = "Petrobras PN",
            regularMarketPrice = BigDecimal("38.50"),
            regularMarketPreviousClose = BigDecimal("38.00"),
            regularMarketChange = BigDecimal("0.50"),
            regularMarketChangePercent = BigDecimal("1.3158"),
            logoUrl = "https://logo",
        )

        val entity = dto.toQuoteEntity(stockAsset(), updatedAt = 123L)

        assertNotNull(entity)
        assertEquals("EQUITY:PETR4", entity!!.quoteKey)
        assertEquals(QuoteSource.EQUITY, entity.source)
        assertEquals(BigDecimal("38.50"), entity.price)
        assertEquals(BigDecimal("1.32"), entity.changePercent)
        assertEquals("Petrobras PN", entity.name)
        assertEquals(123L, entity.updatedAt)
    }

    @Test
    fun `rejects a DTO without a price`() {
        val dto = BrapiQuoteDto(symbol = "PETR4", regularMarketPrice = null)

        assertNull(dto.toQuoteEntity(stockAsset(), updatedAt = 123L))
    }

    @Test
    fun `rejects a zero price so a good cache is not overwritten`() {
        val dto = BrapiQuoteDto(symbol = "PETR4", regularMarketPrice = BigDecimal.ZERO)

        assertNull(dto.toQuoteEntity(stockAsset(), updatedAt = 123L))
    }

    @Test
    fun `maps cash dividends and ignores invalid dates`() {
        val dto = BrapiQuoteDto(
            dividendsData = BrapiDividendsDataDto(
                cashDividends = listOf(
                    BrapiCashDividendDto(
                        paymentDate = "2025-01-15",
                        rate = BigDecimal("1.20"),
                        label = "dividendo",
                    ),
                    BrapiCashDividendDto(
                        paymentDate = "data-invalida",
                        rate = BigDecimal("2.00"),
                    ),
                    BrapiCashDividendDto(paymentDate = "2025-02-15", rate = null),
                ),
            ),
        )

        val entities = dto.toDividendEntities("PETR4", updatedAt = 99L)

        assertEquals(1, entities.size)
        assertEquals(LocalDate.of(2025, 1, 15), entities.first().paymentDate)
        assertEquals("DIVIDENDO", entities.first().label)
        assertEquals(99L, entities.first().updatedAt)
    }

    @Test
    fun `maps historical timestamps in seconds and milliseconds`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val inSeconds = BrapiHistoricalDto(date = 1_700_000_000L, close = BigDecimal("100"))
        val inMillis = BrapiHistoricalDto(date = 1_700_000_000_000L, close = BigDecimal("101"))

        val first = inSeconds.toBenchmarkPoint(zone)
        val second = inMillis.toBenchmarkPoint(zone)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first!!.date, second!!.date)
    }
}
