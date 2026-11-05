package br.com.carteirainvest.domain.usecase.portfolio

import br.com.carteirainvest.domain.model.PortfolioPosition
import br.com.carteirainvest.domain.model.PortfolioSnapshot
import br.com.carteirainvest.domain.model.PortfolioSummary
import br.com.carteirainvest.domain.repository.PortfolioRepository
import br.com.carteirainvest.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ObservePortfolioUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
) {
    operator fun invoke(walletId: String?): Flow<PortfolioSummary> =
        if (walletId.isNullOrBlank()) flowOf(PortfolioSummary.empty(null))
        else portfolioRepository.observePortfolio(walletId)
}

class ObserveAssetPositionsUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
) {
    operator fun invoke(walletId: String?): Flow<List<PortfolioPosition>> =
        if (walletId.isNullOrBlank()) flowOf(emptyList())
        else portfolioRepository.observeAssetPositions(walletId)
}

class ObserveSnapshotsUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
) {
    operator fun invoke(walletId: String?): Flow<List<PortfolioSnapshot>> =
        if (walletId.isNullOrBlank()) flowOf(emptyList())
        else portfolioRepository.observeSnapshots(walletId)
}

class GetPortfolioUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
) {
    suspend operator fun invoke(walletId: String): PortfolioSummary =
        portfolioRepository.getPortfolio(walletId)
}

class CapturePortfolioSnapshotUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
) {
    suspend operator fun invoke(walletId: String) =
        portfolioRepository.captureSnapshot(walletId)
}

class CaptureAllSnapshotsUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val portfolioRepository: PortfolioRepository,
) {
    suspend operator fun invoke() {
        walletRepository.getAllWallets().forEach { wallet ->
            runCatching { portfolioRepository.captureSnapshot(wallet.id) }
        }
    }
}
