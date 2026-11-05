package br.com.carteirainvest.domain.usecase

import br.com.carteirainvest.core.util.FinancialMath
import br.com.carteirainvest.core.util.percentOf
import br.com.carteirainvest.core.util.safeDivide
import br.com.carteirainvest.domain.model.AllocationSlice
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Weighted-average cost basis calculator. All arithmetic is BigDecimal based.
 *
 * Rules:
 * - BUY: adds quantity and purchase cost + fees to the cost basis.
 * - SELL: removes cost basis at the current weighted average and realizes P&L.
 * - DIVIDEND: never changes quantity; it is accumulated separately.
 * - FIXED_INCOME: without a market quote, the position accrues at the annual
 *   rate informed by the user (simple annual composition, 365-day basis).
 */
object PortfolioCalculator {

    fun buildPosition(
        asset: Asset,
        transactions: List<Transaction>,
        quote: Quote?,
        today: LocalDate = LocalDate.now(),
    ): PortfolioPosition {
        val ordered = transactions
            .filter { it.assetId == asset.id }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.createdAt })

        var quantity = BigDecimal.ZERO
        var averagePrice = BigDecimal.ZERO
        var realizedGain = BigDecimal.ZERO
        var dividends = BigDecimal.ZERO
        var firstBuyDate: LocalDate? = null

        ordered.forEach { transaction ->
            when (transaction.type) {
                TransactionType.BUY -> {
                    val buyQty = transaction.quantity.max(BigDecimal.ZERO)
                    if (buyQty.signum() > 0) {
                        val previousCost = averagePrice.multiply(quantity)
                        val addedCost = buyQty.multiply(transaction.unitPrice)
                            .add(transaction.fees.max(BigDecimal.ZERO))
                        quantity = quantity.add(buyQty)
                        averagePrice = previousCost.add(addedCost).safeDivide(quantity)
                        if (firstBuyDate == null) firstBuyDate = transaction.date
                    }
                }

                TransactionType.SELL -> {
                    val sellQty = transaction.quantity.min(quantity).max(BigDecimal.ZERO)
                    if (sellQty.signum() > 0) {
                        val costBasis = averagePrice.multiply(sellQty)
                        val proceeds = sellQty.multiply(transaction.unitPrice)
                            .subtract(transaction.fees.max(BigDecimal.ZERO))
                        realizedGain = realizedGain.add(proceeds.subtract(costBasis))
                        quantity = quantity.subtract(sellQty)
                        if (quantity.signum() == 0) averagePrice = BigDecimal.ZERO
                    }
                }

                TransactionType.DIVIDEND -> {
                    dividends = dividends.add(transaction.dividendAmount ?: BigDecimal.ZERO)
                }
            }
        }

        val validQuote = quote?.takeIf { it.price.signum() > 0 }
        val estimated = !asset.supportsMarketQuote || validQuote == null
        val currentPrice = when {
            asset.type == AssetType.FIXED_INCOME ->
                accruedFixedIncomePrice(asset, averagePrice, firstBuyDate, today)
            validQuote != null -> validQuote.price
            else -> averagePrice
        }

        val investedAmount = FinancialMath.roundMoney(averagePrice.multiply(quantity))
        val currentValue = FinancialMath.roundMoney(currentPrice.multiply(quantity))
        val profitLoss = FinancialMath.roundMoney(currentValue.subtract(investedAmount))
        val profitLossPercent = percentOf(profitLoss, investedAmount).setScale(2, RoundingMode.HALF_UP)

        val dailyChange: BigDecimal
        val dailyChangePercent: BigDecimal
        if (validQuote != null && asset.supportsMarketQuote) {
            dailyChange = FinancialMath.roundMoney(
                validQuote.price
                    .subtract(validQuote.previousClose ?: validQuote.price)
                    .multiply(quantity)
            )
            dailyChangePercent = validQuote.changePercent.setScale(2, RoundingMode.HALF_UP)
        } else {
            val zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            dailyChange = zero
            dailyChangePercent = zero
        }

        return PortfolioPosition(
            asset = asset,
            quantity = quantity,
            averagePrice = averagePrice.setScale(8, RoundingMode.HALF_UP),
            investedAmount = investedAmount,
            currentPrice = currentPrice.setScale(8, RoundingMode.HALF_UP),
            currentValue = currentValue,
            profitLoss = profitLoss,
            profitLossPercent = profitLossPercent,
            dailyChange = dailyChange,
            dailyChangePercent = dailyChangePercent,
            realizedGain = FinancialMath.roundMoney(realizedGain),
            dividendsReceived = FinancialMath.roundMoney(dividends),
            lastQuoteAt = validQuote?.updatedAt,
            priceIsEstimated = estimated,
        )
    }

    fun buildSummary(
        walletId: String?,
        assets: List<Asset>,
        transactions: List<Transaction>,
        quotes: List<Quote>,
        includeClosed: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): PortfolioSummary {
        if (assets.isEmpty()) return PortfolioSummary.empty(walletId)
        val quotesByKey = quotes.associateBy { it.key }
        val transactionsByAsset = transactions.groupBy { it.assetId }

        val allPositions = assets.map { asset ->
            buildPosition(
                asset = asset,
                transactions = transactionsByAsset[asset.id].orEmpty(),
                quote = asset.quoteKey?.let(quotesByKey::get),
                today = today,
            )
        }

        val openPositions = allPositions.filterNot { it.isClosed }
        val visiblePositions = if (includeClosed) {
            openPositions.sortedByDescending { it.currentValue } +
                allPositions.filter { it.isClosed }.sortedByDescending { it.realizedGain }
        } else {
            openPositions.sortedByDescending { it.currentValue }
        }

        val totalInvested = openPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.investedAmount) }
        val totalValue = openPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.currentValue) }
        val unrealizedGain = openPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.profitLoss) }
        val realizedGain = allPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.realizedGain) }
        val dividends = allPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.dividendsReceived) }
        val totalGain = unrealizedGain.add(realizedGain).add(dividends)
        val totalGainPercent = percentOf(totalGain, totalInvested).setScale(2, RoundingMode.HALF_UP)
        val dailyChange = openPositions.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.dailyChange) }
        val previousValue = totalValue.subtract(dailyChange)
        val dailyChangePercent = percentOf(dailyChange, previousValue).setScale(2, RoundingMode.HALF_UP)

        val totalForAllocation = totalValue
        val allocation = AssetType.entries.mapNotNull { type ->
            val value = openPositions.filter { it.asset.type == type }
                .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.currentValue) }
            if (value.signum() > 0) {
                AllocationSlice(
                    type = type,
                    value = value.setScale(2, RoundingMode.HALF_UP),
                    percentage = percentOf(value, totalForAllocation).setScale(2, RoundingMode.HALF_UP),
                )
            } else {
                null
            }
        }

        val lastUpdatedAt = openPositions.mapNotNull { it.lastQuoteAt }.maxOrNull()

        return PortfolioSummary(
            walletId = walletId,
            positions = visiblePositions,
            totalInvested = totalInvested.setScale(2, RoundingMode.HALF_UP),
            totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            unrealizedGain = unrealizedGain.setScale(2, RoundingMode.HALF_UP),
            realizedGain = realizedGain.setScale(2, RoundingMode.HALF_UP),
            dividendsReceived = dividends.setScale(2, RoundingMode.HALF_UP),
            totalGain = totalGain.setScale(2, RoundingMode.HALF_UP),
            totalGainPercent = totalGainPercent,
            dailyChange = dailyChange.setScale(2, RoundingMode.HALF_UP),
            dailyChangePercent = dailyChangePercent,
            allocation = allocation,
            lastUpdatedAt = lastUpdatedAt,
        )
    }

    private fun accruedFixedIncomePrice(
        asset: Asset,
        averagePrice: BigDecimal,
        firstBuyDate: LocalDate?,
        today: LocalDate,
    ): BigDecimal {
        val rate = asset.annualRate
        if (rate == null || rate.signum() <= 0 || firstBuyDate == null) return averagePrice
        val days = ChronoUnit.DAYS.between(firstBuyDate, today).coerceAtLeast(0)
        if (days == 0L) return averagePrice
        val years = BigDecimal.valueOf(days).divide(BigDecimal("365"), 8, RoundingMode.HALF_UP)
        return FinancialMath.compoundAnnual(averagePrice, rate, years)
            .setScale(8, RoundingMode.HALF_UP)
    }
}
