package br.com.carteirainvest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class AssetType {
    STOCK,
    FII,
    ETF,
    CRYPTO,
    FIXED_INCOME,
}

enum class TransactionType {
    BUY,
    SELL,
    DIVIDEND,
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Long,
)

/**
 * The session lives only in EncryptedSharedPreferences. Keeping the biometric
 * flag here allows the UI to lock an already authenticated session on launch.
 */
data class UserSession(
    val userId: String,
    val name: String,
    val email: String,
    val biometricEnabled: Boolean,
)

data class Wallet(
    val id: String,
    val userId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
)

data class Asset(
    val id: String,
    val walletId: String,
    val ticker: String,
    val name: String,
    val type: AssetType,
    val cryptoId: String? = null,
    val annualRate: BigDecimal? = null,
    val maturityDate: LocalDate? = null,
    val createdAt: Long,
) {
    val displayName: String get() = name.ifBlank { ticker }

    val quoteKey: String?
        get() = when (type) {
            AssetType.STOCK, AssetType.FII, AssetType.ETF -> "EQUITY:${ticker.uppercase()}"
            AssetType.CRYPTO -> "CRYPTO:${ticker.uppercase()}"
            AssetType.FIXED_INCOME -> null
        }

    val supportsMarketQuote: Boolean get() = type != AssetType.FIXED_INCOME
}

data class Transaction(
    val id: String,
    val assetId: String,
    val type: TransactionType,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val fees: BigDecimal,
    val dividendAmount: BigDecimal?,
    val date: LocalDate,
    val notes: String,
    val createdAt: Long,
) {
    /** Gross value used for display only; dividend transactions use [dividendAmount]. */
    val grossValue: BigDecimal
        get() = when (type) {
            TransactionType.DIVIDEND -> dividendAmount ?: BigDecimal.ZERO
            TransactionType.BUY, TransactionType.SELL -> quantity.multiply(unitPrice)
        }
}
