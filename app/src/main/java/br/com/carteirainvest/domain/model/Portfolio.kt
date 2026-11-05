package br.com.carteirainvest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class PortfolioPosition(
    val asset: Asset,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
    val investedAmount: BigDecimal,
    val currentPrice: BigDecimal,
    val currentValue: BigDecimal,
    val profitLoss: BigDecimal,
    val profitLossPercent: BigDecimal,
    val dailyChange: BigDecimal,
    val dailyChangePercent: BigDecimal,
    val realizedGain: BigDecimal,
    val dividendsReceived: BigDecimal,
    val lastQuoteAt: Long?,
    val priceIsEstimated: Boolean,
) {
    val isClosed: Boolean get() = quantity.compareTo(BigDecimal.ZERO) <= 0
    val totalGain: BigDecimal get() = profitLoss.add(realizedGain).add(dividendsReceived)
}

data class AllocationSlice(
    val type: AssetType,
    val value: BigDecimal,
    val percentage: BigDecimal,
)

data class PortfolioSummary(
    val walletId: String?,
    val positions: List<PortfolioPosition>,
    val totalInvested: BigDecimal,
    val totalValue: BigDecimal,
    val unrealizedGain: BigDecimal,
    val realizedGain: BigDecimal,
    val dividendsReceived: BigDecimal,
    val totalGain: BigDecimal,
    val totalGainPercent: BigDecimal,
    val dailyChange: BigDecimal,
    val dailyChangePercent: BigDecimal,
    val allocation: List<AllocationSlice>,
    val lastUpdatedAt: Long?,
) {
    val assetCount: Int get() = positions.size

    companion object {
        fun empty(walletId: String?) = PortfolioSummary(
            walletId = walletId,
            positions = emptyList(),
            totalInvested = BigDecimal.ZERO,
            totalValue = BigDecimal.ZERO,
            unrealizedGain = BigDecimal.ZERO,
            realizedGain = BigDecimal.ZERO,
            dividendsReceived = BigDecimal.ZERO,
            totalGain = BigDecimal.ZERO,
            totalGainPercent = BigDecimal.ZERO,
            dailyChange = BigDecimal.ZERO,
            dailyChangePercent = BigDecimal.ZERO,
            allocation = emptyList(),
            lastUpdatedAt = null,
        )
    }
}

data class PortfolioSnapshot(
    val date: LocalDate,
    val value: BigDecimal,
)

data class AssetDetail(
    val asset: Asset,
    val transactions: List<Transaction>,
    val position: PortfolioPosition,
    val quote: Quote?,
)

data class DividendRecord(
    val transactionId: String,
    val assetId: String,
    val ticker: String,
    val assetName: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val notes: String,
)

data class DividendsSummary(
    val records: List<DividendRecord>,
    val total: BigDecimal,
    val monthlyTotal: BigDecimal,
    val currentYearTotal: BigDecimal,
)
