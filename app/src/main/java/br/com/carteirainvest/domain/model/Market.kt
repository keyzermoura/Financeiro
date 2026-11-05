package br.com.carteirainvest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class QuoteSource {
    EQUITY,
    CRYPTO,
}

data class Quote(
    val key: String,
    val ticker: String,
    val source: QuoteSource,
    val price: BigDecimal,
    val previousClose: BigDecimal?,
    val change: BigDecimal,
    val changePercent: BigDecimal,
    val currency: String,
    val name: String?,
    val logoUrl: String?,
    val updatedAt: Long,
    /**
     * `true` when the value comes from Room after a failed remote refresh or when its
     * age already exceeds the TTL for its source. Rooms' `updatedAt` remains the source
     * of truth; the flag is derived on every Flow emission.
     */
    val isStale: Boolean = false,
)

data class RemoteDividend(
    val ticker: String,
    val paymentDate: LocalDate,
    val ratePerShare: BigDecimal,
    val label: String,
    val relatedTo: String?,
    val updatedAt: Long,
)

data class BenchmarkPoint(
    val date: LocalDate,
    val close: BigDecimal,
)

data class RefreshSummary(
    val updated: Int = 0,
    val fresh: Int = 0,
    val failures: Int = 0,
    val messages: List<String> = emptyList(),
) {
    val hasErrors: Boolean get() = failures > 0
    val hasUpdates: Boolean get() = updated > 0

    companion object {
        val Empty = RefreshSummary()
    }
}
