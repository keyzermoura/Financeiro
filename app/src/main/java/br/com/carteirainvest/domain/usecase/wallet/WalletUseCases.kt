package br.com.carteirainvest.domain.usecase.wallet

import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.Wallet
import br.com.carteirainvest.domain.repository.WalletRepository
import java.util.UUID
import javax.inject.Inject

class ObserveWalletsUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    operator fun invoke(userId: String) = walletRepository.observeWallets(userId)
}

class GetAllWalletsUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(): List<Wallet> = walletRepository.getAllWallets()
}

class CreateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(userId: String, name: String, description: String): Wallet {
        val safeName = name.trim()
        if (safeName.length !in 1..40) {
            throw ValidationException("O nome da carteira deve ter entre 1 e 40 caracteres.")
        }
        val safeDescription = description.trim()
        if (safeDescription.length > 120) {
            throw ValidationException("A descrição deve ter no máximo 120 caracteres.")
        }
        val wallet = Wallet(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = safeName,
            description = safeDescription,
            createdAt = System.currentTimeMillis(),
        )
        walletRepository.createWallet(wallet)
        return wallet
    }
}

class UpdateWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {
    suspend operator fun invoke(wallet: Wallet, name: String, description: String): Wallet {
        val safeName = name.trim()
        if (safeName.length !in 1..40) {
            throw ValidationException("O nome da carteira deve ter entre 1 e 40 caracteres.")
        }
        val safeDescription = description.trim()
        if (safeDescription.length > 120) {
            throw ValidationException("A descrição deve ter no máximo 120 caracteres.")
        }
        val updated = wallet.copy(name = safeName, description = safeDescription)
        walletRepository.updateWallet(updated)
        return updated
    }
}

class DeleteWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val walletSelectionManager: WalletSelectionManager,
) {
    suspend operator fun invoke(walletId: String) {
        walletRepository.deleteWallet(walletId)
        if (walletSelectionManager.selectedWalletId.value == walletId) {
            walletSelectionManager.clear()
        }
    }
}
