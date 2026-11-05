package br.com.carteirainvest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import br.com.carteirainvest.data.local.entity.AlertRuleEntity
import br.com.carteirainvest.data.local.entity.AssetEntity
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.entity.PortfolioSnapshotEntity
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import br.com.carteirainvest.data.local.entity.TransactionEntity
import br.com.carteirainvest.data.local.entity.UserEntity
import br.com.carteirainvest.data.local.entity.WalletEntity
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE lower(email) = lower(:email) LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE userId = :userId ORDER BY createdAt ASC")
    fun observeByUser(userId: String): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    suspend fun getAll(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WalletEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: WalletEntity)

    @Query("UPDATE wallets SET name = :name, description = :description WHERE id = :id")
    suspend fun update(id: String, name: String, description: String)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM wallets WHERE userId = :userId")
    suspend fun countByUser(userId: String): Int
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE walletId = :walletId ORDER BY ticker COLLATE NOCASE ASC")
    fun observeByWallet(walletId: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AssetEntity?>

    @Query("SELECT * FROM assets WHERE walletId = :walletId ORDER BY ticker COLLATE NOCASE ASC")
    suspend fun getByWallet(walletId: String): List<AssetEntity>

    @Query("SELECT * FROM assets ORDER BY ticker COLLATE NOCASE ASC")
    suspend fun getAll(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE walletId = :walletId AND ticker = :ticker COLLATE NOCASE LIMIT 1")
    suspend fun findByWalletAndTicker(walletId: String, ticker: String): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(asset: AssetEntity)

    @Query(
        """
        UPDATE assets
        SET ticker = :ticker, name = :name, type = :type, cryptoId = :cryptoId,
            annualRate = :annualRate, maturityDate = :maturityDate
        WHERE id = :id
        """,
    )
    suspend fun update(
        id: String,
        ticker: String,
        name: String,
        type: AssetType,
        cryptoId: String?,
        annualRate: BigDecimal?,
        maturityDate: LocalDate?,
    )

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TransactionDao {
    @Query(
        "SELECT * FROM asset_transactions WHERE assetId = :assetId " +
            "ORDER BY date DESC, createdAt DESC",
    )
    fun observeByAsset(assetId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT t.* FROM asset_transactions t
        INNER JOIN assets a ON a.id = t.assetId
        WHERE a.walletId = :walletId
        ORDER BY t.date DESC, t.createdAt DESC
        """,
    )
    fun observeByWallet(walletId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM asset_transactions WHERE assetId = :assetId ORDER BY date ASC, createdAt ASC")
    suspend fun getByAsset(assetId: String): List<TransactionEntity>

    @Query(
        """
        SELECT t.* FROM asset_transactions t
        INNER JOIN assets a ON a.id = t.assetId
        WHERE a.walletId = :walletId
        ORDER BY t.date ASC, t.createdAt ASC
        """,
    )
    suspend fun getByWallet(walletId: String): List<TransactionEntity>

    @Query("SELECT * FROM asset_transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Query(
        """
        UPDATE asset_transactions
        SET type = :type, quantity = :quantity, unitPrice = :unitPrice, fees = :fees,
            dividendAmount = :dividendAmount, date = :date, notes = :notes
        WHERE id = :id
        """,
    )
    suspend fun update(
        id: String,
        type: TransactionType,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        fees: BigDecimal,
        dividendAmount: BigDecimal?,
        date: LocalDate,
        notes: String,
    )

    @Query("DELETE FROM asset_transactions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes")
    suspend fun getAll(): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE quoteKey IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<QuoteEntity>

    @Upsert
    suspend fun upsertAll(quotes: List<QuoteEntity>)
}

@Dao
interface RemoteDividendDao {
    @Query("SELECT * FROM remote_dividends WHERE ticker = :ticker ORDER BY paymentDate DESC")
    fun observeByTicker(ticker: String): Flow<List<RemoteDividendEntity>>

    @Query("SELECT * FROM remote_dividends WHERE ticker = :ticker ORDER BY paymentDate DESC")
    suspend fun getByTicker(ticker: String): List<RemoteDividendEntity>

    @Upsert
    suspend fun upsertAll(dividends: List<RemoteDividendEntity>)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM portfolio_snapshots WHERE walletId = :walletId ORDER BY date ASC")
    fun observeByWallet(walletId: String): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT * FROM portfolio_snapshots WHERE walletId = :walletId ORDER BY date ASC")
    suspend fun getByWallet(walletId: String): List<PortfolioSnapshotEntity>

    @Query("SELECT * FROM portfolio_snapshots WHERE walletId = :walletId AND date = :date LIMIT 1")
    suspend fun getForDate(walletId: String, date: LocalDate): PortfolioSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: PortfolioSnapshotEntity)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alert_rules WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: String): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getByUser(userId: String): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<AlertRuleEntity>

    @Upsert
    suspend fun upsert(alert: AlertRuleEntity)

    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE alert_rules SET lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun markTriggered(id: String, timestamp: Long)
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates ORDER BY pair ASC")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates ORDER BY pair ASC")
    suspend fun getAll(): List<ExchangeRateEntity>

    @Upsert
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)
}
