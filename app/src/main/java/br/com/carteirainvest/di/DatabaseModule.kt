package br.com.carteirainvest.di

import android.content.Context
import androidx.room.Room
import br.com.carteirainvest.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideUserDao(database: AppDatabase) = database.userDao()

    @Provides
    fun provideWalletDao(database: AppDatabase) = database.walletDao()

    @Provides
    fun provideAssetDao(database: AppDatabase) = database.assetDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase) = database.transactionDao()

    @Provides
    fun provideQuoteDao(database: AppDatabase) = database.quoteDao()

    @Provides
    fun provideRemoteDividendDao(database: AppDatabase) = database.remoteDividendDao()

    @Provides
    fun provideSnapshotDao(database: AppDatabase) = database.snapshotDao()

    @Provides
    fun provideAlertDao(database: AppDatabase) = database.alertDao()

    @Provides
    fun provideExchangeRateDao(database: AppDatabase) = database.exchangeRateDao()
}
