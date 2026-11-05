package br.com.carteirainvest.domain.exception

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

open class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ValidationException(message: String) : AppException(message)

class AuthException(message: String) : AppException(message)

class NotFoundException(message: String) : AppException(message)

class ReportException(message: String, cause: Throwable? = null) : AppException(message, cause)

/** Converts technical failures into messages suitable for a Portuguese UI. */
fun Throwable.toUserMessage(action: String = "Concluir a operação"): String {
    return when (this) {
        is AppException -> message ?: "Não foi possível $action."
        is UnknownHostException, is java.net.ConnectException ->
            "Sem conexão com a internet. Exibindo os dados salvos."
        is SocketTimeoutException ->
            "A conexão demorou demais. Tente novamente em instantes."
        is HttpException -> when (code()) {
            HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                "A API de cotações recusou a requisição. Verifique o token configurado."
            HttpURLConnection.HTTP_NOT_FOUND ->
                "Ativo ou serviço não encontrado na fonte de dados."
            429 -> "Limite de requisições atingido. Aguarde alguns minutos."
            in 500..599 -> "O serviço de cotações está instável. Tente novamente mais tarde."
            else -> "Falha na fonte de dados (HTTP ${code()})."
        }
        is SecurityException -> "Permissão necessária não concedida."
        is IOException -> "Falha de conexão. Verifique sua internet."
        else -> message ?: "Não foi possível $action."
    }
}
