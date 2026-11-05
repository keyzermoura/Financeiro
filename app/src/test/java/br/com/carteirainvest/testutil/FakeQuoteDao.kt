package br.com.carteirainvest.testutil

import br.com.carteirainvest.data.local.dao.QuoteDao
import br.com.carteirainvest.data.local.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeQuoteDao : QuoteDao {
    private val state = MutableStateFlow<Map<String, QuoteEntity>>(emptyMap())

    override fun observeAll(): Flow<List<QuoteEntity>> =
        state.map { it.values.sortedBy { quote -> quote.ticker } }

    override suspend fun getAll(): List<QuoteEntity> = state.value.values.toList()

    override suspend fun getByKeys(keys: List<String>): List<QuoteEntity> =
        keys.mapNotNull { state.value[it] }

    override suspend fun upsertAll(quotes: List<QuoteEntity>) {
        state.update { current ->
            current + quotes.associateBy { it.quoteKey }
        }
    }

    fun snapshot(): List<QuoteEntity> = state.value.values.toList()
}
