package br.com.carteirainvest.domain.usecase.simulation

import br.com.carteirainvest.domain.exception.ValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class SimulateInvestmentUseCaseTest {

    private val useCase = SimulateInvestmentUseCase()

    @Test
    fun `zero rate keeps only contributions`() {
        val result = useCase(
            initialAmount = BigDecimal("1000"),
            monthlyContribution = BigDecimal("100"),
            annualRatePercent = BigDecimal.ZERO,
            months = 12,
        )

        assertDecimal("1000.00", result.initialAmount)
        assertDecimal("100.00", result.monthlyContribution)
        assertDecimal("2200.00", result.totalInvested)
        assertDecimal("2200.00", result.finalValue)
        assertDecimal("0.00", result.earnings)
        assertEquals(13, result.points.size)
        assertEquals(0, result.points.first().month)
        assertEquals(12, result.points.last().month)
        assertDecimal("2200.00", result.points.last().value)
        assertDecimal("2200.00", result.points.last().invested)
    }

    @Test
    fun `annual rate compounds monthly`() {
        val result = useCase(
            initialAmount = BigDecimal("1000"),
            monthlyContribution = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            months = 12,
        )

        assertDecimal("1000.00", result.totalInvested)
        assertDecimal("1120.00", result.finalValue)
        assertDecimal("120.00", result.earnings)
    }

    @Test
    fun `two years of compounding are applied`() {
        val result = useCase(
            initialAmount = BigDecimal("1000"),
            monthlyContribution = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            months = 24,
        )

        assertDecimal("1254.40", result.finalValue)
        assertDecimal("254.40", result.earnings)
    }

    @Test
    fun `contribution is added after the month return`() {
        val result = useCase(
            initialAmount = BigDecimal("1000"),
            monthlyContribution = BigDecimal("100"),
            annualRatePercent = BigDecimal("12"),
            months = 1,
        )

        assertDecimal("1100.00", result.totalInvested)
        assertDecimal("1109.49", result.finalValue)
        assertDecimal("9.49", result.earnings)
    }

    @Test
    fun `one hundred percent annual rate is accepted`() {
        val result = useCase(
            initialAmount = BigDecimal("100"),
            monthlyContribution = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("100"),
            months = 12,
        )

        assertDecimal("200.00", result.finalValue)
    }

    @Test
    fun `points mirror invested and simulated values`() {
        val result = useCase(
            initialAmount = BigDecimal("500"),
            monthlyContribution = BigDecimal("50"),
            annualRatePercent = BigDecimal("6"),
            months = 3,
        )

        assertEquals(4, result.points.size)
        assertEquals(listOf(0, 1, 2, 3), result.points.map { it.month })
        assertDecimal("500.00", result.points[0].invested)
        assertDecimal("500.00", result.points[0].value)
        assertDecimal("550.00", result.points[1].invested)
        assertDecimal("600.00", result.points[2].invested)
        assertDecimal("650.00", result.points[3].invested)
        assertDecimal("650.00", result.totalInvested)
        assertEquals(result.finalValue, result.points.last().value)
    }

    @Test
    fun `rejects negative values`() {
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("-1"), BigDecimal.ZERO, BigDecimal("10"), 12)
        }
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("100"), BigDecimal("-1"), BigDecimal("10"), 12)
        }
    }

    @Test
    fun `rejects empty investment`() {
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("10"), 12)
        }
    }

    @Test
    fun `rejects period outside supported range`() {
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("100"), BigDecimal.ZERO, BigDecimal("10"), 0)
        }
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("100"), BigDecimal.ZERO, BigDecimal("10"), 601)
        }
    }

    @Test
    fun `rejects annual rate outside supported range`() {
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("100"), BigDecimal.ZERO, BigDecimal("-0.01"), 12)
        }
        assertThrows(ValidationException::class.java) {
            useCase(BigDecimal("100"), BigDecimal.ZERO, BigDecimal("100.01"), 12)
        }
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
