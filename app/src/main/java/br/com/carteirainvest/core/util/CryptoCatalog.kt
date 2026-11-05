package br.com.carteirainvest.core.util

/**
 * Small mapping between common tickers and CoinGecko IDs. CoinGecko's public API accepts
 * IDs ("bitcoin"), not symbols ("BTC"). The app still allows any ID to be typed manually
 * when the asset is created, so this catalog is only a convenience for the UI.
 */
object CryptoCatalog {
    val popular: List<Pair<String, String>> = listOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "XRP" to "ripple",
        "ADA" to "cardano",
        "DOGE" to "dogecoin",
        "BNB" to "binancecoin",
        "LTC" to "litecoin",
        "DOT" to "polkadot",
        "MATIC" to "matic-network",
        "AVAX" to "avalanche-2",
        "LINK" to "chainlink",
        "USDT" to "tether",
        "USDC" to "usd-coin",
    )

    private val byTicker: Map<String, String> = popular.toMap()

    /** Explicit user input wins; otherwise fall back to the known ticker mapping. */
    fun resolveId(ticker: String, cryptoId: String?): String? {
        val explicit = cryptoId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return byTicker[ticker.trim().uppercase()]
    }

    fun tickerFor(id: String): String? =
        popular.firstOrNull { (_, coinGeckoId) -> coinGeckoId.equals(id, ignoreCase = true) }?.first
}
