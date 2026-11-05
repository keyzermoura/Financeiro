package br.com.carteirainvest.domain.usecase.exchange

import br.com.carteirainvest.domain.model.ExchangeRate
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveExchangeRatesUseCase @Inject constructor(
    private val repository: ExchangeRateRepository,
) {
    operator fun invoke(): Flow<List<ExchangeRate>> = repository.observeRates()
}

class RefreshExchangeRatesUseCase @Inject constructor(
    private val repository: ExchangeRateRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Resource<List<ExchangeRate>> =
        repository.refresh(force)
}
