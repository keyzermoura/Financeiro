package br.com.carteirainvest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.CashTransactionType
import br.com.carteirainvest.domain.model.QuoteSource
import br.com.carteirainvest.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)],
)
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long,
)

@Entity(
    tableName = "wallets",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId")],
)
data class WalletEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
)

@Entity(
    tableName = "assets",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["walletId", "ticker"], unique = true),
        Index("walletId"),
    ],
)
data class AssetEntity(
    @PrimaryKey val id: String,
    val walletId: String,
    val ticker: String,
    val name: String,
    val type: AssetType,
    val cryptoId: String?,
    val annualRate: BigDecimal?,
    val maturityDate: LocalDate?,
    val createdAt: Long,
)

@Entity(
    tableName = "asset_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assetId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val type: TransactionType,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val fees: BigDecimal,
    val dividendAmount: BigDecimal?,
    val date: LocalDate,
    val notes: String,
    val createdAt: Long,
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey val quoteKey: String,
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
)

@Entity(
    tableName = "remote_dividends",
    primaryKeys = ["ticker", "paymentDate", "label"],
    indices = [Index("ticker")],
)
data class RemoteDividendEntity(
    val ticker: String,
    val paymentDate: LocalDate,
    val label: String,
    val ratePerShare: BigDecimal,
    val relatedTo: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "portfolio_snapshots",
    primaryKeys = ["walletId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("walletId")],
)
data class PortfolioSnapshotEntity(
    val walletId: String,
    val date: LocalDate,
    val value: BigDecimal,
    val createdAt: Long,
)

@Entity(
    tableName = "alert_rules",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId"), Index("walletId"), Index("assetId")],
)
data class AlertRuleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val walletId: String,
    val assetId: String,
    val ticker: String,
    val type: AlertType,
    val threshold: BigDecimal,
    val enabled: Boolean,
    val createdAt: Long,
    val lastTriggeredAt: Long?,
)

@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val pair: String,
    val name: String,
    val bid: BigDecimal,
    val ask: BigDecimal,
    val percentChange: BigDecimal,
    val updatedAt: Long,
)

@Entity(
    tableName = "cash_transactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["walletId", "date"])],
)
data class CashTransactionEntity(
    @PrimaryKey val id: String,
    val walletId: String,
    val type: CashTransactionType,
    val amount: BigDecimal,
    val date: LocalDate,
    val description: String,
    val createdAt: Long,
)

@Entity(
    tableName = "fixed_income_lots",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseTransactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["purchaseTransactionId"], unique = true),
        Index("assetId"),
    ],
)
data class FixedIncomeLotEntity(
    @PrimaryKey val id: String,
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
)
