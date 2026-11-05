package br.com.carteirainvest.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FinancialMathTest {

    @Test
    fun `percentChange computes positive variation`() {
        val result = FinancialMath.percentChange(
            current = BigDecimal("120"),
            previous = BigDecimal("100"),
        )

        assertDecimal("20.00", result)
    }

    @Test
    fun `percentChange computes negative variation`() {
        val result = FinancialMath.percentChange(
            current = BigDecimal("80"),
            previous = BigDecimal("100"),
        )

        assertDecimal("-20.00", result)
    }

    @Test
    fun `percentChange returns zero when previous is zero`() {
        val result = FinancialMath.percentChange(
            current = BigDecimal("150"),
            previous = BigDecimal.ZERO,
        )

        assertDecimal("0", result)
    }

    @Test
    fun `compoundAnnual grows principal for one year`() {
        val result = FinancialMath.compoundAnnual(
            principal = BigDecimal("1000"),
            annualPercent = BigDecimal("10"),
            years = BigDecimal("1"),
        )

        assertDecimal("1100", result)
    }

    @Test
    fun `compoundAnnual compounds over multiple years`() {
        val result = FinancialMath.compoundAnnual(
            principal = BigDecimal("1000"),
            annualPercent = BigDecimal("10"),
            years = BigDecimal("2"),
        )

        assertDecimal("1210", result)
    }

    @Test
    fun `compoundAnnual returns zero for non positive principal`() {
        assertEquals(BigDecimal.ZERO, FinancialMath.compoundAnnual(
            principal = BigDecimal.ZERO,
            annualPercent = BigDecimal("10"),
            years = BigDecimal("1"),
        ))
        assertEquals(BigDecimal.ZERO, FinancialMath.compoundAnnual(
            principal = BigDecimal("-100"),
            annualPercent = BigDecimal("10"),
            years = BigDecimal("1"),
        ))
    }

    @Test
    fun `compoundAnnual keeps principal without positive rate or time`() {
        assertDecimal(
            "1000",
            FinancialMath.compoundAnnual(BigDecimal("1000"), BigDecimal.ZERO, BigDecimal("1")),
        )
        assertDecimal(
            "1000",
            FinancialMath.compoundAnnual(BigDecimal("1000"), BigDecimal("10"), BigDecimal.ZERO),
        )
    }

    @Test
    fun `annualToMonthlyRate composes back to the annual rate`() {
        val monthly = FinancialMath.annualToMonthlyRate(BigDecimal("12"))
        val grown = BigDecimal.ONE.add(monthly).pow(12).toDouble()

        assertEquals(1.12, grown, 1e-9)
    }

    @Test
    fun `annualToMonthlyRate is zero for zero rate`() {
        assertEquals(0.0, FinancialMath.annualToMonthlyRate(BigDecimal.ZERO).toDouble(), 0.0)
    }

    @Test
    fun `annualToMonthlyRate guards invalid negative rate`() {
        assertEquals(
            0,
            BigDecimal.ZERO.compareTo(FinancialMath.annualToMonthlyRate(BigDecimal("-150"))),
        )
    }

    @Test
    fun `roundMoney uses half up rounding to two decimals`() {
        assertDecimal("10.01", FinancialMath.roundMoney(BigDecimal("10.005")))
        assertDecimal("10.00", FinancialMath.roundMoney(BigDecimal("10.004")))
        assertDecimal("-10.01", FinancialMath.roundMoney(BigDecimal("-10.005")))
    }

    @Test
    fun `nonNegative clamps negative values`() {
        assertDecimal("0", FinancialMath.nonNegative(BigDecimal("-0.01")))
        assertDecimal("25.50", FinancialMath.nonNegative(BigDecimal("25.50")))
    }

    @Test
    fun `safeDivide and percentOf avoid division by zero`() {
        assertDecimal("0", BigDecimal("10").safeDivide(BigDecimal.ZERO))
        assertDecimal("0", percentOf(BigDecimal("10"), BigDecimal.ZERO))
        assertDecimal("25", percentOf(BigDecimal("25"), BigDecimal("100")))
        assertTrue(FinancialMath.ZERO.compareTo(BigDecimal.ZERO) == 0)
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        val expectedValue = BigDecimal(expected)
        assertEquals(
            "esperado=$expectedValue obtido=$actual",
            0,
            expectedValue.compareTo(actual),
        )
    }
}
