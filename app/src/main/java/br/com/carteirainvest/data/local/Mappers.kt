package br.com.carteirainvest.data.local

import br.com.carteirainvest.data.local.entity.AlertRuleEntity
import br.com.carteirainvest.data.local.entity.AssetEntity
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.entity.PortfolioSnapshotEntity
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import br.com.carteirainvest.data.local.entity.TransactionEntity
import br.com.carteirainvest.data.local.entity.UserEntity
import br.com.carteirainvest.data.local.entity.WalletEntity
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.PortfolioSnapshot
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.model.RemoteDividend
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.User
import br.com.carteirainvest.domain.model.Wallet
import java.math.BigDecimal

fun UserEntity.toDomain() = User(
    id = id,
    name = name,
    email = email,
    createdAt = createdAt,
)

fun WalletEntity.toDomain() = Wallet(
    id = id,
    userId = userId,
    name = name,
    description = description,
    createdAt = createdAt,
)

fun Wallet.toEntity() = WalletEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    createdAt = createdAt,
)

fun AssetEntity.toDomain() = Asset(
    id = id,
    walletId = walletId,
    ticker = ticker,
    name = name,
    type = type,
    cryptoId = cryptoId,
    annualRate = annualRate,
    maturityDate = maturityDate,
    createdAt = createdAt,
)

fun Asset.toEntity() = AssetEntity(
    id = id,
    walletId = walletId,
    ticker = ticker,
    name = name,
    type = type,
    cryptoId = cryptoId,
    annualRate = annualRate,
    maturityDate = maturityDate,
    createdAt = createdAt,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    assetId = assetId,
    type = type,
    quantity = quantity,
    unitPrice = unitPrice,
    fees = fees,
    dividendAmount = dividendAmount,
    date = date,
    notes = notes,
    createdAt = createdAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    assetId = assetId,
    type = type,
    quantity = quantity,
    unitPrice = unitPrice,
    fees = fees,
    dividendAmount = dividendAmount,
    date = date,
    notes = notes,
    createdAt = createdAt,
)

fun QuoteEntity.toDomain(isStale: Boolean = false) = Quote(
    key = quoteKey,
    ticker = ticker,
    source = source,
    price = price,
    previousClose = previousClose,
    change = change,
    changePercent = changePercent,
    currency = currency,
    name = name,
    logoUrl = logoUrl,
    updatedAt = updatedAt,
    isStale = isStale,
)

fun Quote.toEntity() = QuoteEntity(
    quoteKey = key,
    ticker = ticker,
    source = source,
    price = price,
    previousClose = previousClose,
    change = change,
    changePercent = changePercent,
    currency = currency,
    name = name,
    logoUrl = logoUrl,
    updatedAt = updatedAt,
)

fun RemoteDividendEntity.toDomain() = RemoteDividend(
    ticker = ticker,
    paymentDate = paymentDate,
    ratePerShare = ratePerShare,
    label = label,
    relatedTo = relatedTo,
    updatedAt = updatedAt,
)

fun RemoteDividend.toEntity() = RemoteDividendEntity(
    ticker = ticker,
    paymentDate = paymentDate,
    label = label,
    ratePerShare = ratePerShare,
    relatedTo = relatedTo,
    updatedAt = updatedAt,
)

fun PortfolioSnapshotEntity.toDomain() = PortfolioSnapshot(
    date = date,
    value = value,
)

fun AlertRuleEntity.toDomain() = AlertRule(
    id = id,
    userId = userId,
    walletId = walletId,
    assetId = assetId,
    ticker = ticker,
    type = type,
    threshold = threshold,
    enabled = enabled,
    createdAt = createdAt,
    lastTriggeredAt = lastTriggeredAt,
)

fun AlertRule.toEntity() = AlertRuleEntity(
    id = id,
    userId = userId,
    walletId = walletId,
    assetId = assetId,
    ticker = ticker,
    type = type,
    threshold = threshold,
    enabled = enabled,
    createdAt = createdAt,
    lastTriggeredAt = lastTriggeredAt,
)

fun ExchangeRateEntity.toDomain(isStale: Boolean = false) = ExchangeRate(
    pair = pair,
    name = name,
    bid = bid,
    ask = ask,
    percentChange = percentChange,
    updatedAt = updatedAt,
    isStale = isStale,
)

fun ExchangeRate.toEntity() = ExchangeRateEntity(
    pair = pair,
    name = name,
    bid = bid,
    ask = ask,
    percentChange = percentChange,
    updatedAt = updatedAt,
)

fun BigDecimal.toDbString(): String = toPlainString()
