package br.com.carteirainvest.presentation.screen.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.ReportFile
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.usecase.auth.ObserveSessionUseCase
import br.com.carteirainvest.domain.usecase.report.ExportPortfolioReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreUiState(
    val session: UserSession? = null,
    val isExporting: Boolean = false,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val exportPortfolioReport: ExportPortfolioReportUseCase,
    private val walletSelectionManager: WalletSelectionManager,
) : ViewModel() {

    private val exporting = MutableStateFlow(false)

    val uiState: StateFlow<MoreUiState> = combine(observeSession(), exporting) { session, exporting ->
        MoreUiState(session = session, isExporting = exporting)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoreUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _reports = MutableSharedFlow<ReportFile>(extraBufferCapacity = 1)
    val reports: SharedFlow<ReportFile> = _reports.asSharedFlow()

    fun exportReport() {
        viewModelScope.launch {
            if (exporting.value) return@launch
            exporting.value = true
            try {
                val walletId = walletSelectionManager.selectedWalletId.value
                if (walletId == null) {
                    _messages.emit("Selecione ou crie uma carteira antes de exportar.")
                    return@launch
                }
                val report = exportPortfolioReport(walletId)
                _reports.emit(report)
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("gerar o relatório em PDF"))
            } finally {
                exporting.value = false
            }
        }
    }
}
