package br.com.carteirainvest.data.local

import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.testutil.exchangeRateEntity
import br.com.carteirainvest.testutil.quoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMappersTest {

    @Test
    fun `maps quote entity to domain preserving staleness`() {
        val entity = quoteEntity(updatedAt = 77L)

        val fresh = entity.toDomain()
        val stale = entity.toDomain(isStale = true)

        assertEquals("EQUITY:PETR4", fresh.key)
        assertEquals(QuoteSource.EQUITY, fresh.source)
        assertEquals(77L, fresh.updatedAt)
        assertFalse(fresh.isStale)
        assertTrue(stale.isStale)
    }

    @Test
    fun `maps exchange rate entity to domain preserving staleness`() {
        val entity = exchangeRateEntity(updatedAt = 99L)

        val stale = entity.toDomain(isStale = true)

        assertEquals("USD-BRL", stale.pair)
        assertEquals(99L, stale.updatedAt)
        assertTrue(stale.isStale)
    }
}
