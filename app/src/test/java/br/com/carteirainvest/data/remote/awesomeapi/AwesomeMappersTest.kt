package br.com.carteirainvest.data.remote.awesomeapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AwesomeMappersTest {

    @Test
    fun `maps string fields to a Room entity`() {
        val dto = AwesomeRateDto(
            name = "Dólar/Real",
            bid = "5.4321",
            ask = "5.4330",
            pctChange = "0.256",
            timestamp = "1700000000",
        )

        val entity = dto.toEntity("USD-BRL", nowMillis = 1L)

        assertNotNull(entity)
        assertEquals("USD-BRL", entity!!.pair)
        assertEquals(BigDecimal("5.4321"), entity.bid)
        assertEquals(BigDecimal("5.4330"), entity.ask)
        assertEquals(BigDecimal("0.26"), entity.percentChange)
        assertEquals(1_700_000_000_000L, entity.updatedAt)
    }

    @Test
    fun `falls back to now when the timestamp is absent and ask is missing`() {
        val dto = AwesomeRateDto(bid = "5.00", timestamp = null)

        val entity = dto.toEntity("USD-BRL", nowMillis = 123L)

        assertNotNull(entity)
        assertEquals(BigDecimal("5.0000"), entity!!.ask)
        assertEquals(123L, entity.updatedAt)
    }

    @Test
    fun `rejects invalid bid`() {
        assertNull(AwesomeRateDto(bid = "abc").toEntity("USD-BRL", 1L))
        assertNull(AwesomeRateDto(bid = "0").toEntity("USD-BRL", 1L))
    }
}
