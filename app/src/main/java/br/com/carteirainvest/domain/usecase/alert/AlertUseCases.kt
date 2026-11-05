package br.com.carteirainvest.domain.usecase.alert

import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.AssetType
import br.com.carteirainvest.domain.repository.AlertNotifier
import br.com.carteirainvest.domain.repository.AlertRepository
import br.com.carteirainvest.domain.repository.AssetRepository
import br.com.carteirainvest.domain.repository.QuoteRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class ObserveAlertsUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    operator fun invoke(userId: String) = alertRepository.observeAlerts(userId)
}

class CreatePriceAlertUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    suspend operator fun invoke(
        userId: String,
        asset: Asset,
        thresholdPercent: BigDecimal,
    ): AlertRule {
        if (!asset.supportsMarketQuote) {
            throw ValidationException("Alertas de variação não se aplicam a renda fixa.")
        }
        if (thresholdPercent < BigDecimal("0.1") || thresholdPercent > BigDecimal("50")) {
            throw ValidationException("Informe uma variação entre 0,1% e 50%.")
        }
        val existing = alertRepository.getAlerts(userId)
        if (existing.any { it.assetId == asset.id && it.type == AlertType.PRICE_CHANGE }) {
            throw ValidationException("Já existe um alerta de variação para ${asset.ticker}.")
        }
        val alert = AlertRule(
            id = UUID.randomUUID().toString(),
            userId = userId,
            walletId = asset.walletId,
            assetId = asset.id,
            ticker = asset.ticker,
            type = AlertType.PRICE_CHANGE,
            threshold = thresholdPercent,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            lastTriggeredAt = null,
        )
        alertRepository.saveAlert(alert)
        return alert
    }
}

class CreateMaturityAlertUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    suspend operator fun invoke(
        userId: String,
        asset: Asset,
        daysBefore: BigDecimal,
    ): AlertRule {
        if (asset.type != AssetType.FIXED_INCOME || asset.maturityDate == null) {
            throw ValidationException("Cadastre a data de vencimento do título primeiro.")
        }
        val days = daysBefore.toInt()
        if (days !in 1..365) {
            throw ValidationException("Informe de 1 a 365 dias de antecedência.")
        }
        val existing = alertRepository.getAlerts(userId)
        if (existing.any { it.assetId == asset.id && it.type == AlertType.MATURITY }) {
            throw ValidationException("Já existe um alerta de vencimento para ${asset.ticker}.")
        }
        val alert = AlertRule(
            id = UUID.randomUUID().toString(),
            userId = userId,
            walletId = asset.walletId,
            assetId = asset.id,
            ticker = asset.ticker,
            type = AlertType.MATURITY,
            threshold = BigDecimal(days),
            enabled = true,
            createdAt = System.currentTimeMillis(),
            lastTriggeredAt = null,
        )
        alertRepository.saveAlert(alert)
        return alert
    }
}

class SetAlertEnabledUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    suspend operator fun invoke(alert: AlertRule, enabled: Boolean) {
        alertRepository.saveAlert(alert.copy(enabled = enabled))
    }
}

class DeleteAlertUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
) {
    suspend operator fun invoke(alertId: String) = alertRepository.deleteAlert(alertId)
}

/**
 * Evaluates all enabled rules against the local cache. The sync worker refreshes
 * quotes first, so the data evaluated here is at most [RefreshAllQuotesUseCase]
 * old and normally within the required TTL.
 */
class CheckAlertsUseCase @Inject constructor(
    private val alertRepository: AlertRepository,
    private val assetRepository: AssetRepository,
    private val quoteRepository: QuoteRepository,
    private val notifier: AlertNotifier,
) {
    suspend operator fun invoke(): Int {
        val alerts = alertRepository.getEnabledAlerts()
        if (alerts.isEmpty()) return 0
        val quotes = quoteRepository.getCachedQuotes().associateBy { it.key }
        val now = System.currentTimeMillis()
        var triggered = 0

        alerts.forEach { alert ->
            val asset = assetRepository.getAsset(alert.assetId) ?: return@forEach
            val didNotify = when (alert.type) {
                AlertType.PRICE_CHANGE -> {
                    val quote = asset.quoteKey?.let(quotes::get) ?: return@forEach
                    if (quote.changePercent.abs() < alert.threshold) return@forEach
                    if (alert.lastTriggeredAt != null && now - alert.lastTriggeredAt < PRICE_COOLDOWN) {
                        return@forEach
                    }
                    notifier.notifyPriceChange(alert, asset, quote, quote.changePercent)
                }

                AlertType.MATURITY -> {
                    val maturity = asset.maturityDate ?: return@forEach
                    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), maturity)
                    if (daysLeft !in 0..alert.threshold.toLong()) return@forEach
                    if (
                        alert.lastTriggeredAt != null &&
                        now - alert.lastTriggeredAt < MATURITY_COOLDOWN
                    ) {
                        return@forEach
                    }
                    notifier.notifyMaturity(alert, asset, daysLeft)
                }
            }

            if (didNotify) {
                alertRepository.markTriggered(alert.id, now)
                triggered += 1
            }
        }
        return triggered
    }

    private companion object {
        const val PRICE_COOLDOWN = 12 * 60 * 60 * 1000L
        const val MATURITY_COOLDOWN = 7 * 24 * 60 * 60 * 1000L
    }
}
