package br.com.carteirainvest.presentation.screen.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.usecase.auth.LogoutUseCase
import br.com.carteirainvest.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootUiState(
    val session: UserSession? = null,
    val locked: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val sessionFlow = observeSession()

    private val _uiState = MutableStateFlow(
        RootUiState(
            session = sessionFlow.value,
            locked = sessionFlow.value?.biometricEnabled == true,
            loading = false,
        ),
    )
    val uiState: StateFlow<RootUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionFlow.collect { session ->
                _uiState.update { current ->
                    current.copy(
                        session = session,
                        locked = when {
                            session == null -> false
                            current.session == null -> session.biometricEnabled
                            else -> current.locked
                        },
                        loading = false,
                    )
                }
            }
        }
    }

    fun unlock() {
        _uiState.update { it.copy(locked = false) }
    }

    fun lock() {
        if (_uiState.value.session?.biometricEnabled == true) {
            _uiState.update { it.copy(locked = true) }
        }
    }

    fun logout() {
        logoutUseCase()
    }
}
