package br.com.carteirainvest.domain.usecase.auth

import br.com.carteirainvest.core.security.WalletSelectionManager
import br.com.carteirainvest.domain.exception.ValidationException
import br.com.carteirainvest.domain.model.User
import br.com.carteirainvest.domain.model.UserSession
import br.com.carteirainvest.domain.model.Wallet
import br.com.carteirainvest.domain.repository.AuthRepository
import br.com.carteirainvest.domain.repository.WalletRepository
import br.com.carteirainvest.domain.usecase.validateEmail
import br.com.carteirainvest.domain.usecase.validateName
import br.com.carteirainvest.domain.usecase.validatePassword
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject

class ObserveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): StateFlow<UserSession?> = authRepository.session
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val walletSelectionManager: WalletSelectionManager,
) {
    suspend operator fun invoke(email: String, password: String): User {
        if (password.isEmpty()) throw ValidationException("Informe sua senha.")
        val user = authRepository.authenticate(validateEmail(email), password)
        walletSelectionManager.clear()
        authRepository.startSession(user)
        return user
    }
}

class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository,
    private val walletSelectionManager: WalletSelectionManager,
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ): User {
        val safeName = validateName(name)
        val safeEmail = validateEmail(email)
        validatePassword(password)
        if (password != passwordConfirmation) {
            throw ValidationException("As senhas não conferem.")
        }
        val user = authRepository.register(safeName, safeEmail, password)
        val wallet = Wallet(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            name = "Minha carteira",
            description = "Carteira principal",
            createdAt = System.currentTimeMillis(),
        )
        walletRepository.createWallet(wallet)
        walletSelectionManager.select(wallet.id)
        authRepository.startSession(user)
        return user
    }
}

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val walletSelectionManager: WalletSelectionManager,
) {
    operator fun invoke() {
        authRepository.logout()
        walletSelectionManager.clear()
    }
}

class SetBiometricEnabledUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(enabled: Boolean) {
        if (enabled && authRepository.session.value == null) {
            throw ValidationException("É necessário estar autenticado para ativar a biometria.")
        }
        authRepository.setBiometricEnabled(enabled)
    }
}
