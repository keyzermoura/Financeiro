package br.com.carteirainvest.domain.usecase

import br.com.carteirainvest.domain.exception.ValidationException
import java.time.LocalDate

internal val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
internal val TICKER_REGEX = Regex("^[A-Z0-9][A-Z0-9._\\-]{1,39}$")

internal fun validateEmail(email: String): String {
    val normalized = email.trim().lowercase()
    if (!EMAIL_REGEX.matches(normalized)) {
        throw ValidationException("Informe um e-mail válido.")
    }
    return normalized
}

internal fun validatePassword(password: String) {
    if (password.length < 8) {
        throw ValidationException("A senha deve ter pelo menos 8 caracteres.")
    }
    if (password.length > 128) {
        throw ValidationException("A senha deve ter no máximo 128 caracteres.")
    }
    if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
        throw ValidationException("A senha deve conter letras e números.")
    }
}

internal fun validateName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.length < 2 || trimmed.length > 80) {
        throw ValidationException("O nome deve ter entre 2 e 80 caracteres.")
    }
    return trimmed
}

internal fun validateTicker(ticker: String): String {
    val normalized = ticker.trim().uppercase()
    if (!TICKER_REGEX.matches(normalized)) {
        throw ValidationException("Ticker inválido. Use letras, números, ponto, hífen ou sublinhado.")
    }
    return normalized
}

internal fun validateTransactionDate(date: LocalDate) {
    if (date.isAfter(LocalDate.now())) {
        throw ValidationException("A data da transação não pode estar no futuro.")
    }
}
