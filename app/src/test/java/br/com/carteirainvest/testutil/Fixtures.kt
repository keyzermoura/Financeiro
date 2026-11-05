package br.com.carteirainvest.testutil

import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.entity.QuoteEntity
import java.math.BigDecimal

fun stockAsset(
    id: String = "asset-stock",
    ticker: String = "PETR4",
    name: String = "Petrobras PN",
): Asset = Asset(
    id = id,
    walletId = "wallet-1",
    ticker = ticker,
    name = name,
    type = AssetType.STOCK,
    createdAt = 1_700_000_000_000L,
)

fun cryptoAsset(
    id: String = "asset-crypto",
    ticker: String = "BTC",
    cryptoId: String = "bitcoin",
): Asset = Asset(
    id = id,
    walletId = "wallet-1",
    ticker = ticker,
    name = "Bitcoin",
    type = AssetType.CRYPTO,
    cryptoId = cryptoId,
    createdAt = 1_700_000_000_000L,
)

fun quoteEntity(
    ticker: String = "PETR4",
    source: QuoteSource = QuoteSource.EQUITY,
    price: String = "38.50",
    updatedAt: Long = 1_700_000_000_000L,
): QuoteEntity = QuoteEntity(
    quoteKey = "${source.name}:${ticker.uppercase()}",
    ticker = ticker.uppercase(),
    source = source,
    price = BigDecimal(price),
    previousClose = BigDecimal("38.00"),
    change = BigDecimal("0.50"),
    changePercent = BigDecimal("1.32"),
    currency = "BRL",
    name = "Ativo $ticker",
    logoUrl = null,
    updatedAt = updatedAt,
)

fun quote(
    ticker: String = "PETR4",
    source: QuoteSource = QuoteSource.EQUITY,
    updatedAt: Long = 1_700_000_000_000L,
    isStale: Boolean = false,
): Quote = Quote(
    key = "${source.name}:${ticker.uppercase()}",
    ticker = ticker.uppercase(),
    source = source,
    price = BigDecimal("38.50"),
    previousClose = BigDecimal("38.00"),
    change = BigDecimal("0.50"),
    changePercent = BigDecimal("1.32"),
    currency = "BRL",
    name = "Ativo $ticker",
    logoUrl = null,
    updatedAt = updatedAt,
    isStale = isStale,
)

fun exchangeRateEntity(
    pair: String = "USD-BRL",
    updatedAt: Long = 1_700_000_000_000L,
): ExchangeRateEntity = ExchangeRateEntity(
    pair = pair,
    name = "Dólar/Real",
    bid = BigDecimal("5.4321"),
    ask = BigDecimal("5.4330"),
    percentChange = BigDecimal("0.25"),
    updatedAt = updatedAt,
)

fun exchangeRate(
    pair: String = "USD-BRL",
    updatedAt: Long = 1_700_000_000_000L,
    isStale: Boolean = false,
): ExchangeRate = ExchangeRate(
    pair = pair,
    name = "Dólar/Real",
    bid = BigDecimal("5.4321"),
    ask = BigDecimal("5.4330"),
    percentChange = BigDecimal("0.25"),
    updatedAt = updatedAt,
    isStale = isStale,
)
