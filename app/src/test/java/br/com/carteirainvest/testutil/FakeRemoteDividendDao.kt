package br.com.carteirainvest.testutil

import br.com.carteirainvest.data.local.dao.RemoteDividendDao
import br.com.carteirainvest.data.local.entity.RemoteDividendEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeRemoteDividendDao : RemoteDividendDao {
    private val state = MutableStateFlow<List<RemoteDividendEntity>>(emptyList())

    override fun observeByTicker(ticker: String): Flow<List<RemoteDividendEntity>> =
        state.map { list ->
            list.filter { it.ticker == ticker }.sortedByDescending { it.paymentDate }
        }

    override suspend fun getByTicker(ticker: String): List<RemoteDividendEntity> =
        state.value.filter { it.ticker == ticker }.sortedByDescending { it.paymentDate }

    override suspend fun upsertAll(dividends: List<RemoteDividendEntity>) {
        state.update { current ->
            val byKey = current.associateBy { "${it.ticker}|${it.paymentDate}|${it.label}" }
                .toMutableMap()
            dividends.forEach { dividend ->
                byKey["${dividend.ticker}|${dividend.paymentDate}|${dividend.label}"] = dividend
            }
            byKey.values.toList()
        }
    }
}
