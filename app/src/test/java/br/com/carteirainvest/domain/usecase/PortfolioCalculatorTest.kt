package br.com.carteirainvest.domain.usecase

import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioCalculatorTest {

    @Test
    fun `weighted average includes fees and accumulates across buys`() {
        val asset = stockAsset()
        val position = PortfolioCalculator.buildPosition(
            asset = asset,
            transactions = listOf(
                buy(quantity = "10", unitPrice = "10", fees = "5", date = LocalDate.of(2025, 1, 1)),
                buy(quantity = "10", unitPrice = "20", date = LocalDate.of(2025, 1, 2)),
            ),
            quote = null,
            today = LocalDate.of(2025, 2, 1),
        )

        assertDecimal("20", position.quantity)
        assertDecimal("15.25", position.averagePrice)
        assertDecimal("305.00", position.investedAmount)
        assertDecimal("305.00", position.currentValue)
        assertDecimal("0.00", position.profitLoss)
        assertDecimal("0.00", position.profitLossPercent)
        assertTrue(position.priceIsEstimated)
    }

    @Test
    fun `partial sale realizes gain and keeps weighted average`() {
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(
                buy(quantity = "10", unitPrice = "10", date = LocalDate.of(2025, 1, 1)),
                buy(quantity = "10", unitPrice = "20", date = LocalDate.of(2025, 1, 2)),
                sell(quantity = "5", unitPrice = "25", fees = "2", date = LocalDate.of(2025, 1, 3)),
            ),
            quote = equityQuote(price = "20", previousClose = "18", changePercent = "11.11"),
            today = LocalDate.of(2025, 1, 4),
        )

        assertDecimal("15", position.quantity)
        assertDecimal("15", position.averagePrice)
        assertDecimal("225.00", position.investedAmount)
        assertDecimal("300.00", position.currentValue)
        assertDecimal("75.00", position.profitLoss)
        assertDecimal("33.33", position.profitLossPercent)
        assertDecimal("48.00", position.realizedGain)
        assertDecimal("30.00", position.dailyChange)
        assertDecimal("11.11", position.dailyChangePercent)
        assertFalse(position.isClosed)
    }

    @Test
    fun `full sale closes position and records realized gain`() {
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(
                buy(quantity = "10", unitPrice = "10"),
                sell(quantity = "10", unitPrice = "12", fees = "1", date = LocalDate.of(2025, 1, 2)),
            ),
            quote = null,
            today = LocalDate.of(2025, 1, 3),
        )

        assertDecimal("0", position.quantity)
        assertDecimal("0", position.averagePrice)
        assertDecimal("0.00", position.investedAmount)
        assertDecimal("0.00", position.currentValue)
        assertDecimal("19.00", position.realizedGain)
        assertTrue(position.isClosed)
    }

    @Test
    fun `sale quantity is capped by current position`() {
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(
                buy(quantity = "5", unitPrice = "8"),
                sell(quantity = "10", unitPrice = "9", date = LocalDate.of(2025, 1, 2)),
            ),
            quote = null,
            today = LocalDate.of(2025, 1, 3),
        )

        assertDecimal("0", position.quantity)
        assertDecimal("5.00", position.realizedGain)
        assertTrue(position.isClosed)
    }

    @Test
    fun `dividends accumulate without changing quantity or average`() {
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(
                buy(quantity = "100", unitPrice = "10"),
                dividend(amount = "50", date = LocalDate.of(2025, 2, 1)),
                dividend(amount = "25.50", date = LocalDate.of(2025, 3, 1)),
            ),
            quote = null,
            today = LocalDate.of(2025, 3, 2),
        )

        assertDecimal("100", position.quantity)
        assertDecimal("10", position.averagePrice)
        assertDecimal("1000.00", position.investedAmount)
        assertDecimal("75.50", position.dividendsReceived)
        assertDecimal("0.00", position.profitLoss)
    }

    @Test
    fun `market quote drives valuation and daily change`() {
        val quote = equityQuote(
            price = "15",
            previousClose = "14",
            changePercent = "7.142857",
            updatedAt = 1_700_000_000_000,
        )
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(buy(quantity = "10", unitPrice = "10")),
            quote = quote,
            today = LocalDate.of(2025, 1, 2),
        )

        assertDecimal("100.00", position.investedAmount)
        assertDecimal("150.00", position.currentValue)
        assertDecimal("50.00", position.profitLoss)
        assertDecimal("50.00", position.profitLossPercent)
        assertDecimal("10.00", position.dailyChange)
        assertDecimal("7.14", position.dailyChangePercent)
        assertFalse(position.priceIsEstimated)
        assertEquals(1_700_000_000_000, position.lastQuoteAt)
    }

    @Test
    fun `zero price quote falls back to average price and marks estimate`() {
        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = listOf(buy(quantity = "10", unitPrice = "10")),
            quote = equityQuote(price = "0", previousClose = "14"),
            today = LocalDate.of(2025, 1, 2),
        )

        assertDecimal("10", position.currentPrice)
        assertDecimal("100.00", position.currentValue)
        assertDecimal("0.00", position.dailyChange)
        assertTrue(position.priceIsEstimated)
        assertEquals(null, position.lastQuoteAt)
    }

    @Test
    fun `transactions are filtered by asset and ordered by date`() {
        val transactions = listOf(
            sell(quantity = "5", unitPrice = "30", date = LocalDate.of(2025, 1, 3)),
            buy(quantity = "5", unitPrice = "10", date = LocalDate.of(2025, 1, 1)),
            buy(quantity = "5", unitPrice = "20", date = LocalDate.of(2025, 1, 2)),
            buy(
                assetId = "other-asset",
                quantity = "999",
                unitPrice = "999",
                date = LocalDate.of(2025, 1, 1),
            ),
        )

        val position = PortfolioCalculator.buildPosition(
            asset = stockAsset(),
            transactions = transactions,
            quote = null,
            today = LocalDate.of(2025, 1, 4),
        )

        // Average of 5 @ 10 + 5 @ 20 = 15; sale of 5 @ 30 realizes 75.
        assertDecimal("5", position.quantity)
        assertDecimal("15", position.averagePrice)
        assertDecimal("75.00", position.investedAmount)
        assertDecimal("75.00", position.realizedGain)
    }

    @Test
    fun `fixed income accrues annual rate from first purchase`() {
        val asset = fixedIncomeAsset(annualRate = "10")
        val position = PortfolioCalculator.buildPosition(
            asset = asset,
            transactions = listOf(
                buy(assetId = "fixed-1", quantity = "1", unitPrice = "1000", date = LocalDate.of(2025, 1, 1)),
            ),
            quote = null,
            today = LocalDate.of(2026, 1, 1),
        )

        assertDecimal("1000.00", position.investedAmount)
        assertDecimal("1100", position.currentPrice)
        assertDecimal("1100.00", position.currentValue)
        assertDecimal("100.00", position.profitLoss)
        assertDecimal("10.00", position.profitLossPercent)
        assertTrue(position.priceIsEstimated)
    }

    @Test
    fun `fixed income without rate or elapsed days keeps average price`() {
        val noRate = PortfolioCalculator.buildPosition(
            asset = fixedIncomeAsset(annualRate = null),
            transactions = listOf(
                buy(assetId = "fixed-1", quantity = "1", unitPrice = "1000", date = LocalDate.of(2025, 1, 1)),
            ),
            quote = null,
            today = LocalDate.of(2026, 1, 1),
        )
        val sameDay = PortfolioCalculator.buildPosition(
            asset = fixedIncomeAsset(annualRate = "10"),
            transactions = listOf(
                buy(assetId = "fixed-1", quantity = "1", unitPrice = "1000", date = LocalDate.of(2025, 1, 1)),
            ),
            quote = null,
            today = LocalDate.of(2025, 1, 1),
        )

        assertDecimal("1000", noRate.currentPrice)
        assertDecimal("0.00", noRate.profitLoss)
        assertDecimal("1000", sameDay.currentPrice)
        assertDecimal("0.00", sameDay.profitLoss)
    }

    @Test
    fun `summary aggregates open positions and allocation`() {
        val stock = stockAsset(id = "stock", ticker = "PETR4")
        val crypto = cryptoAsset(id = "crypto", ticker = "BTC")
        val closed = stockAsset(id = "closed", ticker = "MXRF11", type = AssetType.FII)
        val transactions = listOf(
            buy(assetId = "stock", quantity = "10", unitPrice = "10"),
            buy(assetId = "crypto", quantity = "2", unitPrice = "100"),
            buy(assetId = "closed", quantity = "10", unitPrice = "20"),
            sell(
                assetId = "closed",
                quantity = "10",
                unitPrice = "25",
                date = LocalDate.of(2025, 1, 2),
            ),
        )
        val quotes = listOf(
            quote(
                key = "EQUITY:PETR4",
                ticker = "PETR4",
                source = QuoteSource.EQUITY,
                price = "15",
                previousClose = "14",
                changePercent = "7.14",
            ),
            quote(
                key = "CRYPTO:BTC",
                ticker = "BTC",
                source = QuoteSource.CRYPTO,
                price = "150",
                previousClose = "140",
                changePercent = "7.14",
            ),
        )

        val summary = PortfolioCalculator.buildSummary(
            walletId = "wallet-1",
            assets = listOf(stock, crypto, closed),
            transactions = transactions,
            quotes = quotes,
            includeClosed = false,
            today = LocalDate.of(2025, 1, 3),
        )

        assertEquals("wallet-1", summary.walletId)
        assertEquals(2, summary.assetCount)
        assertDecimal("300.00", summary.totalInvested)
        assertDecimal("450.00", summary.totalValue)
        assertDecimal("150.00", summary.unrealizedGain)
        assertDecimal("50.00", summary.realizedGain)
        assertDecimal("0.00", summary.dividendsReceived)
        assertDecimal("200.00", summary.totalGain)
        assertDecimal("66.67", summary.totalGainPercent)
        assertDecimal("30.00", summary.dailyChange)
        assertDecimal("7.14", summary.dailyChangePercent)
        assertEquals(2, summary.allocation.size)
        assertEquals(AssetType.STOCK, summary.allocation[0].type)
        assertDecimal("33.33", summary.allocation[0].percentage)
        assertEquals(AssetType.CRYPTO, summary.allocation[1].type)
        assertDecimal("66.67", summary.allocation[1].percentage)
        assertTrue(summary.positions.none { it.isClosed })
    }

    @Test
    fun `summary can include closed positions at the end`() {
        val stock = stockAsset(id = "stock", ticker = "PETR4")
        val closed = stockAsset(id = "closed", ticker = "MXRF11", type = AssetType.FII)
        val transactions = listOf(
            buy(assetId = "stock", quantity = "10", unitPrice = "10"),
            buy(assetId = "closed", quantity = "10", unitPrice = "20"),
            sell(
                assetId = "closed",
                quantity = "10",
                unitPrice = "25",
                date = LocalDate.of(2025, 1, 2),
            ),
        )

        val summary = PortfolioCalculator.buildSummary(
            walletId = "wallet-1",
            assets = listOf(stock, closed),
            transactions = transactions,
            quotes = listOf(equityQuote(price = "15", previousClose = "14")),
            includeClosed = true,
            today = LocalDate.of(2025, 1, 3),
        )

        assertEquals(2, summary.positions.size)
        assertEquals("PETR4", summary.positions[0].asset.ticker)
        assertEquals("MXRF11", summary.positions[1].asset.ticker)
        assertTrue(summary.positions[1].isClosed)
        assertDecimal("50.00", summary.realizedGain)
    }

    @Test
    fun `summary is empty without assets`() {
        val summary = PortfolioCalculator.buildSummary(
            walletId = "wallet-1",
            assets = emptyList(),
            transactions = emptyList(),
            quotes = emptyList(),
        )

        assertTrue(summary.positions.isEmpty())
        assertTrue(summary.allocation.isEmpty())
        assertEquals("wallet-1", summary.walletId)
        assertDecimal("0", summary.totalValue)
    }

    private fun stockAsset(
        id: String = "asset-1",
        ticker: String = "PETR4",
        type: AssetType = AssetType.STOCK,
    ) = Asset(
        id = id,
        walletId = "wallet-1",
        ticker = ticker,
        name = ticker,
        type = type,
        createdAt = 0L,
    )

    private fun cryptoAsset(
        id: String = "crypto-1",
        ticker: String = "BTC",
    ) = Asset(
        id = id,
        walletId = "wallet-1",
        ticker = ticker,
        name = ticker,
        type = AssetType.CRYPTO,
        cryptoId = ticker.lowercase(),
        createdAt = 0L,
    )

    private fun fixedIncomeAsset(annualRate: String?) = Asset(
        id = "fixed-1",
        walletId = "wallet-1",
        ticker = "CDB",
        name = "CDB Teste",
        type = AssetType.FIXED_INCOME,
        annualRate = annualRate?.let(::BigDecimal),
        maturityDate = LocalDate.of(2027, 1, 1),
        createdAt = 0L,
    )

    private fun buy(
        assetId: String = "asset-1",
        quantity: String,
        unitPrice: String,
        fees: String = "0",
        date: LocalDate = LocalDate.of(2025, 1, 1),
        createdAt: Long = 0L,
    ) = tx(
        assetId = assetId,
        type = TransactionType.BUY,
        quantity = quantity,
        unitPrice = unitPrice,
        fees = fees,
        date = date,
        createdAt = createdAt,
    )

    private fun sell(
        assetId: String = "asset-1",
        quantity: String,
        unitPrice: String,
        fees: String = "0",
        date: LocalDate = LocalDate.of(2025, 1, 1),
        createdAt: Long = 0L,
    ) = tx(
        assetId = assetId,
        type = TransactionType.SELL,
        quantity = quantity,
        unitPrice = unitPrice,
        fees = fees,
        date = date,
        createdAt = createdAt,
    )

    private fun dividend(
        assetId: String = "asset-1",
        amount: String,
        date: LocalDate = LocalDate.of(2025, 1, 1),
    ) = tx(
        assetId = assetId,
        type = TransactionType.DIVIDEND,
        dividendAmount = amount,
        date = date,
    )

    private fun tx(
        assetId: String,
        type: TransactionType,
        quantity: String = "0",
        unitPrice: String = "0",
        fees: String = "0",
        dividendAmount: String? = null,
        date: LocalDate = LocalDate.of(2025, 1, 1),
        createdAt: Long = 0L,
    ) = Transaction(
        id = "tx-$assetId-${type.name}-$date-$quantity-$unitPrice",
        assetId = assetId,
        type = type,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        fees = BigDecimal(fees),
        dividendAmount = dividendAmount?.let(::BigDecimal),
        date = date,
        notes = "",
        createdAt = createdAt,
    )

    private fun equityQuote(
        key: String = "EQUITY:PETR4",
        price: String,
        previousClose: String? = null,
        changePercent: String = "0",
        updatedAt: Long = 1_700_000_000_000,
    ) = quote(
        key = key,
        ticker = "PETR4",
        source = QuoteSource.EQUITY,
        price = price,
        previousClose = previousClose,
        changePercent = changePercent,
        updatedAt = updatedAt,
    )

    private fun quote(
        key: String,
        ticker: String,
        source: QuoteSource,
        price: String,
        previousClose: String? = null,
        changePercent: String = "0",
        updatedAt: Long = 1_700_000_000_000,
    ): Quote {
        val priceValue = BigDecimal(price)
        val previous = previousClose?.let(::BigDecimal)
        return Quote(
            key = key,
            ticker = ticker,
            source = source,
            price = priceValue,
            previousClose = previous,
            change = previous?.let { priceValue.subtract(it) } ?: BigDecimal.ZERO,
            changePercent = BigDecimal(changePercent),
            currency = "BRL",
            name = null,
            logoUrl = null,
            updatedAt = updatedAt,
        )
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
