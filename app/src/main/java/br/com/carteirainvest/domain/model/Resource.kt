package br.com.carteirainvest.domain.model

/**
 * Result of a data operation that may come from the network or from the Room cache.
 *
 * [Success.isStale] is `true` when the repository could not refresh the data remotely and
 * returned the last cached value instead. The UI should show this state explicitly so the
 * user never mistakes an old quote for a live one.
 *
 * Failures are modelled as values instead of exceptions. Exceptions are reserved for
 * programming errors and cancellation.
 */
sealed interface Resource<out T> {

    data class Success<T>(
        val data: T,
        override val isStale: Boolean = false,
    ) : Resource<T>

    data class Failure(
        val error: DataError,
    ) : Resource<Nothing>

    val isSuccess: Boolean get() = this is Success

    /**
     * `true` only for a [Success] that used cached data after a failed refresh.
     * [Failure] always returns `false`.
     */
    val isStale: Boolean get() = false

    fun dataOrNull(): T? = (this as? Success)?.data
}

/**
 * Typed transport/API failures. Keeping them separate from user-facing strings makes
 * retry decisions testable and prevents UI wording from leaking into the data layer.
 */
sealed interface DataError {

    /** Device offline, DNS failure or connection refused. */
    data object NoConnection : DataError

    /** Connect/read/write/call timeout. */
    data object Timeout : DataError

    /** Any non-retryable 4xx response (401, 403, 404, 422 etc.). */
    data class Http4xx(
        val code: Int,
        val serverMessage: String? = null,
    ) : DataError

    /** Server-side failure; retryable with exponential backoff. */
    data class Http5xx(
        val code: Int,
        val serverMessage: String? = null,
    ) : DataError

    /**
     * HTTP 429. [retryAfterMillis] comes from the `Retry-After` header when the server
     * provides it; otherwise the retry policy applies exponential backoff.
     */
    data class RateLimited(
        val retryAfterMillis: Long? = null,
        val serverMessage: String? = null,
    ) : DataError

    /** Malformed JSON, unexpected field type or empty body. */
    data class Parsing(
        val serverMessage: String? = null,
    ) : DataError

    data class Unknown(
        val serverMessage: String? = null,
    ) : DataError

    /** Whether [NetworkRetryPolicy] should try the call again. */
    val isRetryable: Boolean
        get() = when (this) {
            NoConnection, Timeout, is Http5xx, is RateLimited -> true
            is Http4xx, is Parsing, is Unknown -> false
        }
}

/** Portuguese messages used by ViewModels and Workers. */
fun DataError.userMessage(action: String = "concluir a operação"): String = when (this) {
    DataError.NoConnection -> "Sem conexão com a internet. Exibindo os dados salvos."
    DataError.Timeout -> "A conexão demorou demais. Tente novamente em instantes."
    is DataError.RateLimited -> {
        val wait = retryAfterMillis
            ?.let { millis -> ((millis + 999) / 1_000).coerceAtLeast(1) }
            ?.let { seconds -> " Aguarde cerca de ${seconds}s." }
            .orEmpty()
        "Limite de requisições da API atingido.$wait"
    }
    is DataError.Http4xx -> when (code) {
        401, 403 -> "A API recusou a requisição. Verifique o token configurado."
        404 -> "Ativo ou serviço não encontrado na fonte de dados."
        else -> serverMessage?.takeIf { it.isNotBlank() }
            ?: "A fonte de dados rejeitou a requisição (HTTP $code)."
    }
    is DataError.Http5xx -> "O serviço de cotações está instável (HTTP $code). Tente mais tarde."
    is DataError.Parsing -> "A resposta da API veio em um formato inesperado."
    is DataError.Unknown -> serverMessage?.takeIf { it.isNotBlank() }
        ?: "Não foi possível $action."
}
