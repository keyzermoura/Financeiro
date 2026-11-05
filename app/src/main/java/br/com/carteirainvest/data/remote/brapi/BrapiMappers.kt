package br.com.carteirainvest.data.remote.brapi

import br.com.carteirainvest.core.util.FinancialMath
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.BenchmarkPoint
import br.com.carteirainvest.domain.model.QuoteSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * DTO -> Room mappers for Brapi. Missing or non-positive prices are rejected here so the
 * repository never replaces a good cached quote with an invalid zero.
 */
fun BrapiQuoteDto.toQuoteEntity(asset: Asset, updatedAt: Long): QuoteEntity? {
    val price = regularMarketPrice ?: return null
    if (price.signum() <= 0) return null

    val previousClose = regularMarketPreviousClose
    val change = regularMarketChange
        ?: previousClose?.let { price.subtract(it) }
        ?: BigDecimal.ZERO
    val changePercent = regularMarketChangePercent
        ?: previousClose?.let { FinancialMath.percentChange(price, it) }
        ?: BigDecimal.ZERO
    return QuoteEntity(
        quoteKey = "EQUITY:${asset.ticker.uppercase()}",
        ticker = asset.ticker.uppercase(),
        source = QuoteSource.EQUITY,
        price = price,
        previousClose = previousClose,
        change = change,
        changePercent = changePercent.setScale(2, RoundingMode.HALF_UP),
        currency = "BRL",
        name = shortName ?: longName ?: asset.name,
        logoUrl = logoUrl,
        updatedAt = updatedAt,
    )
}

fun BrapiQuoteDto.toDividendEntities(
    ticker: String,
    updatedAt: Long,
): List<RemoteDividendEntity> =
    dividendsData?.cashDividends.orEmpty().mapNotNull { dividend ->
        val rate = dividend.rate ?: return@mapNotNull null
        val paymentDate = dividend.paymentDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@mapNotNull null
        if (rate.signum() <= 0) return@mapNotNull null
        RemoteDividendEntity(
            ticker = ticker.uppercase(),
            paymentDate = paymentDate,
            label = dividend.label?.takeIf { it.isNotBlank() }?.uppercase() ?: "PROVENTO",
            ratePerShare = rate,
            relatedTo = dividend.relatedTo,
            updatedAt = updatedAt,
        )
    }

fun BrapiHistoricalDto.toBenchmarkPoint(zone: ZoneId): BenchmarkPoint? {
    val epoch = date ?: return null
    val close = close ?: return null
    val instant = if (epoch > 10_000_000_000L) {
        Instant.ofEpochMilli(epoch)
    } else {
        Instant.ofEpochSecond(epoch)
    }
    return BenchmarkPoint(
        date = instant.atZone(zone).toLocalDate(),
        close = close,
    )
}
