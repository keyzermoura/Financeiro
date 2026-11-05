package br.com.carteirainvest.core.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Small financial math helpers that are deliberately free of Android imports so
 * they can be unit tested on the JVM.
 */
object FinancialMath {

    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)

    fun percentChange(current: BigDecimal, previous: BigDecimal): BigDecimal {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return current.subtract(previous)
            .multiply(BigDecimal("100"))
            .divide(previous, 8, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
    }

    fun compoundAnnual(principal: BigDecimal, annualPercent: BigDecimal, years: BigDecimal): BigDecimal {
        if (principal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO
        if (annualPercent.compareTo(BigDecimal.ZERO) <= 0 || years.compareTo(BigDecimal.ZERO) <= 0) {
            return principal
        }
        val growth = Math.pow(
            1.0 + annualPercent.toDouble() / 100.0,
            years.toDouble(),
        )
        return principal.multiply(BigDecimal.valueOf(growth)).setScale(8, RoundingMode.HALF_UP)
    }

    fun annualToMonthlyRate(annualPercent: BigDecimal): BigDecimal {
        val annual = annualPercent.toDouble() / 100.0
        if (annual <= -1.0) return BigDecimal.ZERO
        val monthly = Math.pow(1.0 + annual, 1.0 / 12.0) - 1.0
        return BigDecimal.valueOf(monthly)
    }

    fun roundMoney(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun nonNegative(value: BigDecimal): BigDecimal =
        if (value < BigDecimal.ZERO) BigDecimal.ZERO else value
}
