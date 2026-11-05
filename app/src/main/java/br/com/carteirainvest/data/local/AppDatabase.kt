package br.com.carteirainvest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import br.com.carteirainvest.data.local.dao.AlertDao
import br.com.carteirainvest.data.local.dao.CashTransactionDao
import br.com.carteirainvest.data.local.dao.FixedIncomeLotDao
import br.com.carteirainvest.data.local.dao.AssetDao
import br.com.carteirainvest.data.local.dao.ExchangeRateDao
import br.com.carteirainvest.data.local.dao.QuoteDao
import br.com.carteirainvest.data.local.dao.RemoteDividendDao
import br.com.carteirainvest.data.local.dao.SnapshotDao
import br.com.carteirainvest.data.local.dao.TransactionDao
import br.com.carteirainvest.data.local.dao.UserDao
import br.com.carteirainvest.data.local.dao.WalletDao
import br.com.carteirainvest.data.local.entity.AlertRuleEntity
import br.com.carteirainvest.data.local.entity.CashTransactionEntity
import br.com.carteirainvest.data.local.entity.FixedIncomeLotEntity
import br.com.carteirainvest.data.local.entity.AssetEntity
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import br.com.carteirainvest.data.local.entity.PortfolioSnapshotEntity
import br.com.carteirainvest.data.local.entity.QuoteEntity
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import br.com.carteirainvest.data.local.entity.TransactionEntity
import br.com.carteirainvest.data.local.entity.UserEntity
import br.com.carteirainvest.data.local.entity.WalletEntity

@Database(
    entities = [
        UserEntity::class,
        WalletEntity::class,
        AssetEntity::class,
        TransactionEntity::class,
        QuoteEntity::class,
        RemoteDividendEntity::class,
        PortfolioSnapshotEntity::class,
        AlertRuleEntity::class,
        ExchangeRateEntity::class,
        CashTransactionEntity::class,
        FixedIncomeLotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun walletDao(): WalletDao
    abstract fun assetDao(): AssetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun quoteDao(): QuoteDao
    abstract fun remoteDividendDao(): RemoteDividendDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun alertDao(): AlertDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun cashTransactionDao(): CashTransactionDao
    abstract fun fixedIncomeLotDao(): FixedIncomeLotDao

    companion object {
        const val NAME = "carteira_invest.db"
    }
}
