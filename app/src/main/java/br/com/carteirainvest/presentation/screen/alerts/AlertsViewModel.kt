package br.com.carteirainvest.presentation.screen.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.AlertType
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.usecase.alert.CheckAlertsUseCase
import br.com.carteirainvest.domain.usecase.alert.CreateMaturityAlertUseCase
import br.com.carteirainvest.domain.usecase.alert.CreatePriceAlertUseCase
import br.com.carteirainvest.domain.usecase.alert.DeleteAlertUseCase
import br.com.carteirainvest.domain.usecase.alert.ObserveAlertsUseCase
import br.com.carteirainvest.domain.usecase.alert.SetAlertEnabledUseCase
import br.com.carteirainvest.domain.usecase.asset.ObserveAssetsUseCase
import br.com.carteirainvest.domain.usecase.auth.ObserveSessionUseCase
import br.com.carteirainvest.domain.usecase.market.RefreshWalletQuotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class AlertsUiState(
    val alerts: List<AlertRule> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val isBusy: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertsViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeAlerts: ObserveAlertsUseCase,
    observeAssets: ObserveAssetsUseCase,
    private val createPriceAlert: CreatePriceAlertUseCase,
    private val createMaturityAlert: CreateMaturityAlertUseCase,
    private val setAlertEnabled: SetAlertEnabledUseCase,
    private val deleteAlert: DeleteAlertUseCase,
    private val checkAlerts: CheckAlertsUseCase,
    private val refreshWalletQuotes: RefreshWalletQuotesUseCase,
    private val walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val sessionFlow = observeSession()
    private val userId = sessionFlow
        .map { it?.userId }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = sessionFlow.value?.userId,
        )
    private val alertsFlow = userId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else observeAlerts(id)
    }
    private val assetsFlow = walletSelectionManager.selectedWalletId.flatMapLatest { walletId ->
        if (walletId == null) flowOf(emptyList()) else observeAssets(walletId)
    }
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<AlertsUiState> = combine(alertsFlow, assetsFlow, busy) { alerts, assets, busy ->
        AlertsUiState(alerts = alerts, assets = assets, isBusy = busy)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlertsUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun createAlert(assetId: String, type: AlertType, thresholdText: String) {
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            try {
                val currentUserId = userId.value
                    ?: throw ValidationException("Sessão expirada. Entre novamente.")
                val asset = uiState.value.assets.firstOrNull { it.id == assetId }
                    ?: throw ValidationException("Selecione o ativo do alerta.")
                val threshold = parseThreshold(thresholdText)
                when (type) {
                    AlertType.PRICE_CHANGE -> createPriceAlert(currentUserId, asset, threshold)
                    AlertType.MATURITY -> createMaturityAlert(currentUserId, asset, threshold)
                }
                _messages.emit("Alerta criado.")
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("criar o alerta"))
            } finally {
                busy.value = false
            }
        }
    }

    fun toggleAlert(alert: AlertRule, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { setAlertEnabled(alert, enabled) }
                .onSuccess {
                    _messages.emit(if (enabled) "Alerta ativado." else "Alerta pausado.")
                }
                .onFailure { _messages.emit(it.toUserMessage("atualizar o alerta")) }
        }
    }

    fun deleteAlert(alert: AlertRule) {
        viewModelScope.launch {
            runCatching { deleteAlert(alert.id) }
                .onSuccess { _messages.emit("Alerta excluído.") }
                .onFailure { _messages.emit(it.toUserMessage("excluir o alerta")) }
        }
    }

    fun checkNow() {
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            try {
                val walletId = walletSelectionManager.selectedWalletId.value
                if (walletId != null) {
                    refreshWalletQuotes(walletId, force = true)
                }
                val triggered = checkAlerts()
                _messages.emit(
                    if (triggered > 0) {
                        "$triggered alerta(s) disparado(s). Confira as notificações."
                    } else {
                        "Nenhum alerta precisa ser disparado agora."
                    },
                )
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("verificar os alertas"))
            } finally {
                busy.value = false
            }
        }
    }

    private fun parseThreshold(raw: String): BigDecimal {
        val value = BrFormat.parseDecimal(raw)
            ?: throw ValidationException("Informe um limite válido.")
        if (value.signum() <= 0) throw ValidationException("O limite deve ser maior que zero.")
        return value
    }
}
