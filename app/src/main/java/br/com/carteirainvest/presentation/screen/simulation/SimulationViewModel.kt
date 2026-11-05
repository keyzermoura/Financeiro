package br.com.carteirainvest.presentation.screen.simulation

import androidx.lifecycle.ViewModel
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.SimulationResult
import br.com.carteirainvest.domain.usecase.simulation.SimulateInvestmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SimulationUiState(
    val result: SimulationResult? = null,
)

@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val simulateInvestment: SimulateInvestmentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SimulationUiState())
    val uiState: StateFlow<SimulationUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun simulate(
        initialText: String,
        monthlyText: String,
        annualRateText: String,
        monthsText: String,
    ) {
        try {
            val initial = if (initialText.isBlank()) java.math.BigDecimal.ZERO else {
                BrFormat.parseDecimal(initialText)
                    ?: throw ValidationException("Valor inicial inválido.")
            }
            val monthly = if (monthlyText.isBlank()) java.math.BigDecimal.ZERO else {
                BrFormat.parseDecimal(monthlyText)
                    ?: throw ValidationException("Aporte mensal inválido.")
            }
            val annualRate = BrFormat.parseDecimal(annualRateText)
                ?: throw ValidationException("Informe a rentabilidade anual esperada.")
            val months = monthsText.trim().toIntOrNull()
                ?: throw ValidationException("Informe o prazo em meses.")
            val result = simulateInvestment(initial, monthly, annualRate, months)
            _uiState.value = SimulationUiState(result = result)
        } catch (error: Exception) {
            _messages.tryEmit(error.toUserMessage("simular o investimento"))
        }
    }

    fun clear() {
        _uiState.value = SimulationUiState()
    }
}
