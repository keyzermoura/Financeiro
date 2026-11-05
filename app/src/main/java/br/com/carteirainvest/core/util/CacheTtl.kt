package br.com.carteirainvest.core.util

import br.com.carteirainvest.domain.model.QuoteSource

/**
 * Cache lifetimes for market data. These values are product decisions, not API limits:
 *
 * - B3 equities, FIIs and ETFs: 5 minutes. Brapi's free plan has a ~30-minute quote delay,
 *   so two refreshes inside the same half hour usually return the same price. Keeping a
 *   short TTL still avoids hammering the API and makes the app feel current when the
 *   provider does update.
 * - Crypto: 60 seconds. CoinGecko is much more volatile and its free tier tolerates a
 *   higher request frequency if we respect 429 responses.
 * - FX: 1 hour. AwesomeAPI rates do not change often enough to justify more calls.
 */
object CacheTtl {
    const val CRYPTO_SECONDS = 60L
    const val EQUITY_MINUTES = 5L
    const val FX_MINUTES = 60L

    const val CRYPTO_MILLIS = CRYPTO_SECONDS * 1_000L
    const val EQUITY_MILLIS = EQUITY_MINUTES * 60_000L
    const val FX_MILLIS = FX_MINUTES * 60_000L

    /** Dividends change rarely; six hours avoids spending Brapi quota on historical data. */
    const val DIVIDEND_MILLIS = 6 * 60 * 60 * 1_000L

    fun ttlFor(source: QuoteSource): Long = when (source) {
        QuoteSource.CRYPTO -> CRYPTO_MILLIS
        QuoteSource.EQUITY -> EQUITY_MILLIS
    }

    fun isFresh(updatedAt: Long, nowMillis: Long, ttlMillis: Long): Boolean =
        nowMillis - updatedAt < ttlMillis

    fun isStale(updatedAt: Long, nowMillis: Long, ttlMillis: Long): Boolean =
        !isFresh(updatedAt, nowMillis, ttlMillis)
}
