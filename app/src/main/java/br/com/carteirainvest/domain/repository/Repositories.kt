package br.com.carteirainvest.domain.repository

import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.User
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.model.Wallet
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: kotlinx.coroutines.flow.StateFlow<UserSession?>

    suspend fun register(name: String, email: String, password: String): User
    suspend fun authenticate(email: String, password: String): User
    fun startSession(user: User)
    fun logout()
    fun setBiometricEnabled(enabled: Boolean)
}

interface WalletRepository {
    fun observeWallets(userId: String): Flow<List<Wallet>>
    suspend fun getAllWallets(): List<Wallet>
    suspend fun getWallet(id: String): Wallet?
    suspend fun createWallet(wallet: Wallet)
    suspend fun updateWallet(wallet: Wallet)
    suspend fun deleteWallet(id: String)
}

interface AssetRepository {
    fun observeAssets(walletId: String): Flow<List<Asset>>
    fun observeAsset(id: String): Flow<Asset?>
    suspend fun getAssets(walletId: String): List<Asset>
    suspend fun getAllAssets(): List<Asset>
    suspend fun getAsset(id: String): Asset?
    suspend fun findByWalletAndTicker(walletId: String, ticker: String): Asset?
    suspend fun createAsset(asset: Asset, firstTransaction: Transaction?)
    suspend fun updateAsset(asset: Asset)
    suspend fun deleteAsset(id: String)

    fun observeTransactions(assetId: String): Flow<List<Transaction>>
    fun observeWalletTransactions(walletId: String): Flow<List<Transaction>>
    suspend fun getWalletTransactions(walletId: String): List<Transaction>
    suspend fun getTransactions(assetId: String): List<Transaction>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)

    suspend fun countAssetsByType(walletId: String, type: AssetType): Int
}
