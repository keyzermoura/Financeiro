package br.com.carteirainvest.data.remote.coingecko

import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.testutil.cryptoAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CoinGeckoMappersTest {

    @Test
    fun `maps a crypto quote and derives previous close`() {
        val dto = CoinGeckoPriceDto(
            brl = BigDecimal("100.00"),
            brl24hChange = BigDecimal("10.00"),
        )

        val entity = dto.toQuoteEntity(cryptoAsset(), updatedAt = 50L)

        assertNotNull(entity)
        assertEquals("CRYPTO:BTC", entity!!.quoteKey)
        assertEquals(QuoteSource.CRYPTO, entity.source)
        assertEquals(BigDecimal("100.00"), entity.price)
        assertEquals(BigDecimal("10.00"), entity.changePercent)
        assertEquals(0, entity.previousClose!!.compareTo(BigDecimal("90.90909091")))
        assertEquals(50L, entity.updatedAt)
    }

    @Test
    fun `rejects missing and non positive prices`() {
        assertNull(CoinGeckoPriceDto(brl = null).toQuoteEntity(cryptoAsset(), 1L))
        assertNull(
            CoinGeckoPriceDto(brl = BigDecimal.ZERO).toQuoteEntity(cryptoAsset(), 1L),
        )
    }

    @Test
    fun `guards the previous close when the 24h drop is nearly total`() {
        val dto = CoinGeckoPriceDto(
            brl = BigDecimal("1.00"),
            brl24hChange = BigDecimal("-100.00"),
        )

        val entity = dto.toQuoteEntity(cryptoAsset(), updatedAt = 1L)

        assertNotNull(entity)
        assertNull(entity!!.previousClose)
        assertEquals(BigDecimal.ZERO, entity.change)
    }
}
