package br.com.carteirainvest.domain.usecase.asset

import br.com.carteirainvest.core.util.CryptoCatalog
import br.com.carteirainvest.core.util.FinancialMath
import br.com.carteirainvest.core.util.percentOf
import br.com.carteirainvest.domain.exception.NotFoundException
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetDetail
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.model.DividendRecord
import br.com.carteirainvest.domain.model.DividendsSummary
import br.com.carteirainvest.domain.model.Transaction
import br.com.carteirainvest.domain.model.TransactionType
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.QuoteRepository
import br.com.carteirainvest.domain.usecase.PortfolioCalculator
import br.com.carteirainvest.domain.usecase.validateTicker
import br.com.carteirainvest.domain.usecase.validateTransactionDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

class ObserveAssetsUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    operator fun invoke(walletId: String): Flow<List<Asset>> = assetRepository.observeAssets(walletId)
}

class ObserveAssetUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    operator fun invoke(assetId: String): Flow<Asset?> = assetRepository.observeAsset(assetId)
}

class GetAssetUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(assetId: String): Asset? = assetRepository.getAsset(assetId)
}

class ObserveTransactionsUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    operator fun invoke(assetId: String): Flow<List<Transaction>> =
        assetRepository.observeTransactions(assetId)
}

class ObserveAssetDetailUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
) {
    operator fun invoke(assetId: String): Flow<AssetDetail?> = combine(
        assetRepository.observeAsset(assetId),
        assetRepository.observeTransactions(assetId),
        quoteRepository.observeQuotes(),
    ) { asset, transactions, quotes ->
        if (asset == null) return@combine null
        val quote = asset.quoteKey?.let { key -> quotes.firstOrNull { it.key == key } }
        AssetDetail(
            asset = asset,
            transactions = transactions,
            position = PortfolioCalculator.buildPosition(asset, transactions, quote),
            quote = quote,
        )
    }
}

class CreateAssetUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(
        walletId: String,
        ticker: String,
        name: String,
        type: AssetType,
        cryptoId: String?,
        annualRate: BigDecimal?,
        maturityDate: LocalDate?,
        initialQuantity: BigDecimal,
        initialPrice: BigDecimal,
        initialFees: BigDecimal,
        initialDate: LocalDate,
        notes: String,
    ): Asset {
        if (walletId.isBlank()) throw ValidationException("Selecione uma carteira.")
        val safeTicker = validateTicker(ticker)
        val safeName = name.trim().ifBlank { safeTicker }
        if (safeName.length > 80) throw ValidationException("O nome do ativo deve ter até 80 caracteres.")
        if (assetRepository.findByWalletAndTicker(walletId, safeTicker) != null) {
            throw ValidationException("Já existe um ativo com este ticker nesta carteira.")
        }
        val normalizedCryptoId = validateTypeSpecificFields(type, safeTicker, cryptoId, annualRate, maturityDate)
        if (initialQuantity.signum() <= 0) {
            throw ValidationException("A quantidade inicial deve ser maior que zero.")
        }
        if (initialPrice.signum() < 0 || initialFees.signum() < 0) {
            throw ValidationException("Preço e taxas não podem ser negativos.")
        }
        validateTransactionDate(initialDate)
        if (notes.length > 500) throw ValidationException("As observações devem ter até 500 caracteres.")

        val now = System.currentTimeMillis()
        val asset = Asset(
            id = UUID.randomUUID().toString(),
            walletId = walletId,
            ticker = safeTicker,
            name = safeName,
            type = type,
            cryptoId = normalizedCryptoId,
            annualRate = if (type == AssetType.FIXED_INCOME) annualRate else null,
            maturityDate = if (type == AssetType.FIXED_INCOME) maturityDate else null,
            createdAt = now,
        )
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            assetId = asset.id,
            type = TransactionType.BUY,
            quantity = initialQuantity,
            unitPrice = initialPrice,
            fees = initialFees,
            dividendAmount = null,
            date = initialDate,
            notes = notes.trim(),
            createdAt = now,
        )
        assetRepository.createAsset(asset, transaction)
        return asset
    }
}

class UpdateAssetUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(
        assetId: String,
        ticker: String,
        name: String,
        type: AssetType,
        cryptoId: String?,
        annualRate: BigDecimal?,
        maturityDate: LocalDate?,
    ): Asset {
        val current = assetRepository.getAsset(assetId)
            ?: throw NotFoundException("Ativo não encontrado.")
        val safeTicker = validateTicker(ticker)
        val safeName = name.trim().ifBlank { safeTicker }
        if (safeName.length > 80) throw ValidationException("O nome do ativo deve ter até 80 caracteres.")
        val duplicate = assetRepository.findByWalletAndTicker(current.walletId, safeTicker)
        if (duplicate != null && duplicate.id != current.id) {
            throw ValidationException("Já existe um ativo com este ticker nesta carteira.")
        }
        val normalizedCryptoId = validateTypeSpecificFields(type, safeTicker, cryptoId, annualRate, maturityDate)
        val updated = current.copy(
            ticker = safeTicker,
            name = safeName,
            type = type,
            cryptoId = normalizedCryptoId,
            annualRate = if (type == AssetType.FIXED_INCOME) annualRate else null,
            maturityDate = if (type == AssetType.FIXED_INCOME) maturityDate else null,
        )
        assetRepository.updateAsset(updated)
        return updated
    }
}

class DeleteAssetUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(assetId: String) = assetRepository.deleteAsset(assetId)
}

class AddTransactionUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(
        assetId: String,
        type: TransactionType,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        fees: BigDecimal,
        dividendAmount: BigDecimal?,
        date: LocalDate,
        notes: String,
    ): Transaction {
        val asset = assetRepository.getAsset(assetId)
            ?: throw NotFoundException("Ativo não encontrado.")
        val normalized = normalizeTransaction(type, quantity, unitPrice, fees, dividendAmount, date, notes)
        if (type == TransactionType.SELL) {
            val transactions = assetRepository.getTransactions(assetId)
            val available = PortfolioCalculator.buildPosition(asset, transactions, null).quantity
            if (normalized.quantity > available) {
                throw ValidationException(
                    "Quantidade insuficiente. Você possui ${available.stripTrailingZeros().toPlainString()} unidades.",
                )
            }
        }
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            assetId = assetId,
            type = normalized.type,
            quantity = normalized.quantity,
            unitPrice = normalized.unitPrice,
            fees = normalized.fees,
            dividendAmount = normalized.dividendAmount,
            date = date,
            notes = notes.trim(),
            createdAt = System.currentTimeMillis(),
        )
        assetRepository.addTransaction(transaction)
        return transaction
    }
}

class UpdateTransactionUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(
        transactionId: String,
        assetId: String,
        type: TransactionType,
        quantity: BigDecimal,
        unitPrice: BigDecimal,
        fees: BigDecimal,
        dividendAmount: BigDecimal?,
        date: LocalDate,
        notes: String,
    ): Transaction {
        val asset = assetRepository.getAsset(assetId)
            ?: throw NotFoundException("Ativo não encontrado.")
        val allTransactions = assetRepository.getTransactions(assetId)
        val original = allTransactions.firstOrNull { it.id == transactionId }
            ?: throw NotFoundException("Transação não encontrada.")
        val normalized = normalizeTransaction(type, quantity, unitPrice, fees, dividendAmount, date, notes)
        if (type == TransactionType.SELL) {
            val available = PortfolioCalculator.buildPosition(
                asset,
                allTransactions.filterNot { it.id == transactionId },
                null,
            ).quantity
            if (normalized.quantity > available) {
                throw ValidationException(
                    "Quantidade insuficiente. Você possui ${available.stripTrailingZeros().toPlainString()} unidades.",
                )
            }
        }
        val updated = original.copy(
            type = normalized.type,
            quantity = normalized.quantity,
            unitPrice = normalized.unitPrice,
            fees = normalized.fees,
            dividendAmount = normalized.dividendAmount,
            date = date,
            notes = notes.trim(),
        )
        assetRepository.updateTransaction(updated)
        return updated
    }
}

class DeleteTransactionUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    suspend operator fun invoke(transactionId: String) =
        assetRepository.deleteTransaction(transactionId)
}

class ObserveDividendsUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
) {
    operator fun invoke(walletId: String): Flow<DividendsSummary> = combine(
        assetRepository.observeAssets(walletId),
        assetRepository.observeWalletTransactions(walletId),
    ) { assets, transactions ->
        val assetsById = assets.associateBy { it.id }
        val records = transactions
            .filter { it.type == TransactionType.DIVIDEND }
            .mapNotNull { transaction ->
                val asset = assetsById[transaction.assetId] ?: return@mapNotNull null
                val amount = transaction.dividendAmount ?: return@mapNotNull null
                if (amount.signum() <= 0) return@mapNotNull null
                DividendRecord(
                    transactionId = transaction.id,
                    assetId = asset.id,
                    ticker = asset.ticker,
                    assetName = asset.displayName,
                    amount = amount.setScale(2, RoundingMode.HALF_UP),
                    date = transaction.date,
                    notes = transaction.notes,
                )
            }
            .sortedByDescending { it.date }

        val total = records.fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amount) }
        val now = LocalDate.now()
        val currentMonth = YearMonth.from(now)
        val monthlyTotal = records
            .filter { YearMonth.from(it.date) == currentMonth }
            .fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amount) }
        val yearlyTotal = records
            .filter { it.date.year == now.year }
            .fold(BigDecimal.ZERO) { acc, record -> acc.add(record.amount) }

        DividendsSummary(
            records = records,
            total = total.setScale(2, RoundingMode.HALF_UP),
            monthlyTotal = monthlyTotal.setScale(2, RoundingMode.HALF_UP),
            currentYearTotal = yearlyTotal.setScale(2, RoundingMode.HALF_UP),
        )
    }
}

private data class NormalizedTransaction(
    val type: TransactionType,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val fees: BigDecimal,
    val dividendAmount: BigDecimal?,
)

private fun normalizeTransaction(
    type: TransactionType,
    quantity: BigDecimal,
    unitPrice: BigDecimal,
    fees: BigDecimal,
    dividendAmount: BigDecimal?,
    date: LocalDate,
    notes: String,
): NormalizedTransaction {
    validateTransactionDate(date)
    if (notes.length > 500) throw ValidationException("As observações devem ter até 500 caracteres.")
    return when (type) {
        TransactionType.DIVIDEND -> {
            val amount = dividendAmount ?: BigDecimal.ZERO
            if (amount.signum() <= 0) throw ValidationException("Informe o valor do provento.")
            NormalizedTransaction(
                type = type,
                quantity = BigDecimal.ZERO,
                unitPrice = BigDecimal.ZERO,
                fees = BigDecimal.ZERO,
                dividendAmount = amount,
            )
        }

        TransactionType.BUY, TransactionType.SELL -> {
            if (quantity.signum() <= 0) throw ValidationException("A quantidade deve ser maior que zero.")
            if (unitPrice.signum() < 0) throw ValidationException("O preço unitário não pode ser negativo.")
            if (fees.signum() < 0) throw ValidationException("As taxas não podem ser negativas.")
            NormalizedTransaction(
                type = type,
                quantity = quantity,
                unitPrice = unitPrice,
                fees = fees,
                dividendAmount = null,
            )
        }
    }
}

private fun validateTypeSpecificFields(
    type: AssetType,
    ticker: String,
    cryptoId: String?,
    annualRate: BigDecimal?,
    maturityDate: LocalDate?,
): String? {
    return when (type) {
        AssetType.CRYPTO -> {
            val resolved = CryptoCatalog.resolveId(ticker, cryptoId)
                ?: throw ValidationException("Informe o ID CoinGecko do criptoativo.")
            resolved
        }

        AssetType.FIXED_INCOME -> {
            if (annualRate != null && annualRate.signum() < 0) {
                throw ValidationException("A taxa anual não pode ser negativa.")
            }
            if (annualRate != null && annualRate > BigDecimal("100")) {
                throw ValidationException("A taxa anual deve ser de no máximo 100%.")
            }
            if (maturityDate != null && maturityDate.isBefore(LocalDate.of(1900, 1, 1))) {
                throw ValidationException("Data de vencimento inválida.")
            }
            null
        }

        else -> null
    }
}
