package br.com.carteirainvest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class AlertType {
    PRICE_CHANGE,
    MATURITY,
}

data class AlertRule(
    val id: String,
    val userId: String,
    val walletId: String,
    val assetId: String,
    val ticker: String,
    val type: AlertType,
    /** Percentage for PRICE_CHANGE, days-before for MATURITY. */
    val threshold: BigDecimal,
    val enabled: Boolean,
    val createdAt: Long,
    val lastTriggeredAt: Long?,
)

data class ExchangeRate(
    val pair: String,
    val name: String,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val percentChange: BigDecimal,
    val updatedAt: Long,
    /** Derived from [updatedAt] and the 1-hour FX TTL, or from a failed refresh. */
    val isStale: Boolean = false,
)

data class SimulationPoint(
    val month: Int,
    val invested: BigDecimal,
    val value: BigDecimal,
)

data class SimulationResult(
    val initialAmount: BigDecimal,
    val monthlyContribution: BigDecimal,
    val annualRatePercent: BigDecimal,
    val months: Int,
    val totalInvested: BigDecimal,
    val finalValue: BigDecimal,
    val earnings: BigDecimal,
    val points: List<SimulationPoint>,
)

data class ReportFile(
    val absolutePath: String,
    val displayName: String,
    val walletName: String,
)
