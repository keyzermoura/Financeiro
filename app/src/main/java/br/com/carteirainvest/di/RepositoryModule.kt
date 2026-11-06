package br.com.carteirainvest.di

import br.com.carteirainvest.data.notification.AndroidAlertNotifier
import br.com.carteirainvest.data.report.PdfPortfolioReportExporter
import br.com.carteirainvest.data.repository.AlertRepositoryImpl
import br.com.carteirainvest.data.repository.AssetRepositoryImpl
import br.com.carteirainvest.data.repository.AuthRepositoryImpl
import br.com.carteirainvest.data.repository.CashRepositoryImpl
import br.com.carteirainvest.data.repository.ExchangeRateRepositoryImpl
import br.com.carteirainvest.data.repository.PortfolioRepositoryImpl
import br.com.carteirainvest.data.repository.QuoteRepositoryImpl
import br.com.carteirainvest.data.repository.WalletRepositoryImpl
import br.com.carteirainvest.domain.repository.AlertNotifier
import br.com.carteirainvest.domain.repository.AlertRepository
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.AuthRepository
import br.com.carteirainvest.domain.repository.CashRepository
import br.com.carteirainvest.domain.repository.ExchangeRateRepository
import br.com.carteirainvest.domain.repository.PortfolioReportExporter
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.repository.QuoteRepository
import br.com.carteirainvest.domain.repository.WalletRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCashRepository(impl: CashRepositoryImpl): CashRepository

    @Binds
    @Singleton
    abstract fun bindWalletRepository(impl: WalletRepositoryImpl): WalletRepository

    @Binds
    @Singleton
    abstract fun bindAssetRepository(impl: AssetRepositoryImpl): AssetRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: PortfolioRepositoryImpl): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(impl: ExchangeRateRepositoryImpl): ExchangeRateRepository

    @Binds
    @Singleton
    abstract fun bindReportExporter(impl: PdfPortfolioReportExporter): PortfolioReportExporter

    @Binds
    @Singleton
    abstract fun bindAlertNotifier(impl: AndroidAlertNotifier): AlertNotifier
}
