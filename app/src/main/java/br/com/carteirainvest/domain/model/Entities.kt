package br.com.carteirainvest.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class AssetType { STOCK, FII, ETF, CRYPTO, FIXED_INCOME }
enum class TransactionType { BUY, SELL, DIVIDEND }
enum class CashTransactionType { OPENING_BALANCE, DEPOSIT, WITHDRAWAL }

data class User(val id: String, val name: String, val email: String, val createdAt: Long)
data class UserSession(val userId: String, val name: String, val email: String, val biometricEnabled: Boolean)
data class Wallet(val id: String, val userId: String, val name: String, val description: String, val createdAt: Long)

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
    val grossValue: BigDecimal
        get() = when (type) {
            TransactionType.DIVIDEND -> dividendAmount ?: BigDecimal.ZERO
            TransactionType.BUY, TransactionType.SELL -> quantity.multiply(unitPrice)
        }
}

/** External cash event. Asset BUY/SELL/DIVIDEND remain in asset_transactions. */
data class CashTransaction(
    val id: String,
    val walletId: String,
    val type: CashTransactionType,
    val amount: BigDecimal,
    val date: LocalDate,
    val description: String,
    val createdAt: Long,
    val sourceTransactionId: String? = null,
)

/** One application; rate and maturity are snapshotted at purchase. */
data class FixedIncomeLot(
    val id: String,
    val assetId: String,
    val purchaseTransactionId: String,
    val originalQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val principalAmount: BigDecimal,
    val remainingPrincipalAmount: BigDecimal,
    val acquisitionFees: BigDecimal,
    val annualRate: BigDecimal?,
    val maturityDate: LocalDate?,
    val createdAt: Long,
    val isMigrated: Boolean = false,
)

/** Persisted link between a partial/full redemption and the lot it consumes. */
data class FixedIncomeRedemptionAllocation(
    val id: String,
    val redemptionTransactionId: String,
    val lotId: String,
    val quantity: BigDecimal,
    val principalAmount: BigDecimal,
    val redemptionAmount: BigDecimal,
    val fees: BigDecimal,
    val createdAt: Long,
)
