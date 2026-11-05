package br.com.carteirainvest.data.repository

import br.com.carteirainvest.core.util.IoDispatcher
import br.com.carteirainvest.data.local.dao.WalletDao
import br.com.carteirainvest.data.local.toDomain
import br.com.carteirainvest.data.local.toEntity
import br.com.carteirainvest.domain.model.Wallet
import br.com.carteirainvest.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val walletDao: WalletDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WalletRepository {

    override fun observeWallets(userId: String): Flow<List<Wallet>> =
        walletDao.observeByUser(userId).map { wallets -> wallets.map { it.toDomain() } }

    override suspend fun getAllWallets(): List<Wallet> = withContext(ioDispatcher) {
        walletDao.getAll().map { it.toDomain() }
    }

    override suspend fun getWallet(id: String): Wallet? = withContext(ioDispatcher) {
        walletDao.findById(id)?.toDomain()
    }

    override suspend fun createWallet(wallet: Wallet) = withContext(ioDispatcher) {
        walletDao.insert(wallet.toEntity())
    }

    override suspend fun updateWallet(wallet: Wallet) = withContext(ioDispatcher) {
        walletDao.update(wallet.id, wallet.name, wallet.description)
    }

    override suspend fun deleteWallet(id: String) = withContext(ioDispatcher) {
        walletDao.delete(id)
    }
}
