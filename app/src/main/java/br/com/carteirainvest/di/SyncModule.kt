package br.com.carteirainvest.di

import br.com.carteirainvest.data.notification.SyncFailureNotifier
import br.com.carteirainvest.data.notification.SyncFailureReporter
import br.com.carteirainvest.data.sync.DefaultMarketSynchronizer
import br.com.carteirainvest.data.sync.MarketSynchronizer
import br.com.carteirainvest.data.worker.SyncScheduleConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindMarketSynchronizer(impl: DefaultMarketSynchronizer): MarketSynchronizer

    @Binds
    @Singleton
    abstract fun bindSyncFailureReporter(impl: SyncFailureNotifier): SyncFailureReporter

    companion object {
        @Provides
        @Singleton
        fun provideSyncScheduleConfig(): SyncScheduleConfig = SyncScheduleConfig()
    }
}
