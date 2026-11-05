package br.com.carteirainvest.domain.usecase.simulation

import br.com.carteirainvest.core.util.FinancialMath
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.SimulationPoint
import br.com.carteirainvest.domain.model.SimulationResult
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class SimulateInvestmentUseCase @Inject constructor() {

    operator fun invoke(
        initialAmount: BigDecimal,
        monthlyContribution: BigDecimal,
        annualRatePercent: BigDecimal,
        months: Int,
    ): SimulationResult {
        if (initialAmount.signum() < 0 || monthlyContribution.signum() < 0) {
            throw ValidationException("Os valores não podem ser negativos.")
        }
        if (initialAmount.signum() == 0 && monthlyContribution.signum() == 0) {
            throw ValidationException("Informe um valor inicial ou um aporte mensal.")
        }
        if (months !in 1..600) {
            throw ValidationException("Informe um prazo entre 1 e 600 meses.")
        }
        if (annualRatePercent.signum() < 0 || annualRatePercent > BigDecimal("100")) {
            throw ValidationException("Informe uma rentabilidade anual entre 0% e 100%.")
        }

        val monthlyRate = FinancialMath.annualToMonthlyRate(annualRatePercent)
        var balance = initialAmount
        var invested = initialAmount
        val points = mutableListOf(
            SimulationPoint(
                month = 0,
                invested = invested.setScale(2, RoundingMode.HALF_UP),
                value = balance.setScale(2, RoundingMode.HALF_UP),
            ),
        )

        for (month in 1..months) {
            balance = balance.multiply(BigDecimal.ONE.add(monthlyRate))
                .add(monthlyContribution)
            invested = invested.add(monthlyContribution)
            points.add(
                SimulationPoint(
                    month = month,
                    invested = invested.setScale(2, RoundingMode.HALF_UP),
                    value = balance.setScale(2, RoundingMode.HALF_UP),
                ),
            )
        }

        val finalValue = balance.setScale(2, RoundingMode.HALF_UP)
        val totalInvested = invested.setScale(2, RoundingMode.HALF_UP)
        return SimulationResult(
            initialAmount = initialAmount.setScale(2, RoundingMode.HALF_UP),
            monthlyContribution = monthlyContribution.setScale(2, RoundingMode.HALF_UP),
            annualRatePercent = annualRatePercent.setScale(2, RoundingMode.HALF_UP),
            months = months,
            totalInvested = totalInvested,
            finalValue = finalValue,
            earnings = finalValue.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP),
            points = points,
        )
    }
}
