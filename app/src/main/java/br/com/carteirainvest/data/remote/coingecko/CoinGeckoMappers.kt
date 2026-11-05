package br.com.carteirainvest.data.remote.coingecko

import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.QuoteSource
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * CoinGecko -> Room mapper. The 24h percentage is converted into an approximate
 * previous close, because the domain keeps the same shape for B3 and crypto quotes.
 */
fun CoinGeckoPriceDto.toQuoteEntity(asset: Asset, updatedAt: Long): QuoteEntity? {
    val price = brl ?: return null
    if (price.signum() <= 0) return null

    val changePercent = brl24hChange ?: BigDecimal.ZERO
    val previousClose = if (changePercent <= BigDecimal("-99.999")) {
        null
    } else {
        val factor = BigDecimal.ONE.add(
            changePercent.divide(BigDecimal("100"), 8, RoundingMode.HALF_UP),
        )
        price.divide(factor, 8, RoundingMode.HALF_UP)
    }
    val change = previousClose?.let { price.subtract(it) } ?: BigDecimal.ZERO
    return QuoteEntity(
        quoteKey = "CRYPTO:${asset.ticker.uppercase()}",
        ticker = asset.ticker.uppercase(),
        source = QuoteSource.CRYPTO,
        price = price,
        previousClose = previousClose,
        change = change,
        changePercent = changePercent.setScale(2, RoundingMode.HALF_UP),
        currency = "BRL",
        name = asset.name,
        logoUrl = null,
        updatedAt = updatedAt,
    )
}
