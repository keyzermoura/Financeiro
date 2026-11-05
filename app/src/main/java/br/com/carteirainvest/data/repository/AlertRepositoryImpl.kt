package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.dao.AlertDao
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.local.toEntity
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.repository.AlertRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val alertDao: AlertDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlertRepository {

    override fun observeAlerts(userId: String): Flow<List<AlertRule>> =
        alertDao.observeByUser(userId).map { alerts -> alerts.map { it.toDomain() } }

    override suspend fun getAlerts(userId: String): List<AlertRule> = withContext(ioDispatcher) {
        alertDao.getByUser(userId).map { it.toDomain() }
    }

    override suspend fun getEnabledAlerts(): List<AlertRule> = withContext(ioDispatcher) {
        alertDao.getEnabled().map { it.toDomain() }
    }

    override suspend fun saveAlert(alert: AlertRule) = withContext(ioDispatcher) {
        alertDao.upsert(alert.toEntity())
    }

    override suspend fun deleteAlert(id: String) = withContext(ioDispatcher) {
        alertDao.delete(id)
    }

    override suspend fun markTriggered(id: String, timestamp: Long) = withContext(ioDispatcher) {
        alertDao.markTriggered(id, timestamp)
    }
}
