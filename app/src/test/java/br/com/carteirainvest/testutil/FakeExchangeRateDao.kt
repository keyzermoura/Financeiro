package br.com.carteirainvest.testutil

import br.com.carteirainvest.data.local.dao.ExchangeRateDao
import br.com.carteirainvest.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeExchangeRateDao : ExchangeRateDao {
    private val state = MutableStateFlow<Map<String, ExchangeRateEntity>>(emptyMap())

    override fun observeAll(): Flow<List<ExchangeRateEntity>> =
        state.map { it.values.sortedBy { rate -> rate.pair } }

    override suspend fun getAll(): List<ExchangeRateEntity> = state.value.values.toList()

    override suspend fun upsertAll(rates: List<ExchangeRateEntity>) {
        state.update { current -> current + rates.associateBy { it.pair } }
    }

    fun snapshot(): List<ExchangeRateEntity> = state.value.values.toList()
}
