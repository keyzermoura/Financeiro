package br.com.carteirainvest.data.remote.awesomeapi

import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * AwesomeAPI sends all numeric fields as strings. Parsing happens at the mapper
 * boundary; the rest of the app only sees `BigDecimal` and a Room entity.
 */
fun AwesomeRateDto.toEntity(pair: String, nowMillis: Long): ExchangeRateEntity? {
    val bid = bid?.toBigDecimalOrNull() ?: return null
    if (bid.signum() <= 0) return null
    val ask = ask?.toBigDecimalOrNull() ?: bid
    val percent = pctChange?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val timestamp = timestamp?.toLongOrNull()?.let { seconds ->
        if (seconds > 10_000_000_000L) seconds else seconds * 1_000L
    } ?: nowMillis

    return ExchangeRateEntity(
        pair = pair,
        name = name ?: pair,
        bid = bid.setScale(4, RoundingMode.HALF_UP),
        ask = ask.setScale(4, RoundingMode.HALF_UP),
        percentChange = percent.setScale(2, RoundingMode.HALF_UP),
        updatedAt = timestamp,
    )
}
