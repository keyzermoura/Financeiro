package br.com.carteirainvest.presentation.screen.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.domain.exception.toUserMessage
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.usecase.auth.ObserveSessionUseCase
import br.com.carteirainvest.domain.usecase.auth.SetBiometricEnabledUseCase
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

data class SecurityUiState(
    val session: UserSession? = null,
    val isUpdating: Boolean = false,
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val setBiometricEnabled: SetBiometricEnabledUseCase,
) : ViewModel() {

    private val updating = MutableStateFlow(false)

    val uiState: StateFlow<SecurityUiState> = combine(observeSession(), updating) { session, updating ->
        SecurityUiState(session = session, isUpdating = updating)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityUiState(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (updating.value) return@launch
            updating.value = true
            try {
                setBiometricEnabled(enabled)
                _messages.emit(
                    if (enabled) "Biometria ativada. O app será bloqueado ao abrir."
                    else "Biometria desativada.",
                )
            } catch (error: Exception) {
                _messages.emit(error.toUserMessage("alterar a biometria"))
            } finally {
                updating.value = false
            }
        }
    }
}
