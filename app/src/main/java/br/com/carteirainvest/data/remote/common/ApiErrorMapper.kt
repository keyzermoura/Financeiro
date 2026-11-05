package br.com.carteirainvest.data.remote.common

import br.com.carteirainvest.domain.model.DataError
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import okhttp3.Headers
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Converts Retrofit/OkHttp/Gson failures into [DataError] values. Nothing here throws:
 * the repository maps a failure to a stale cache entry or to `Resource.Failure`.
 */
fun Throwable.toDataError(nowMillis: Long = System.currentTimeMillis()): DataError = when (this) {
    is HttpException -> response()?.toHttpError(code(), nowMillis)
        ?: DataError.Unknown(message)
    is SocketTimeoutException -> DataError.Timeout
    is UnknownHostException, is ConnectException -> DataError.NoConnection
    is JsonParseException, is MalformedJsonException, is EOFException, is NumberFormatException ->
        DataError.Parsing(message)
    is IOException -> DataError.NoConnection
    else -> DataError.Unknown(message)
}

private fun retrofit2.Response<*>.toHttpError(code: Int, nowMillis: Long): DataError {
    val body = runCatching { errorBody()?.string() }.getOrNull()?.take(500)
    return when {
        code == HTTP_TOO_MANY_REQUESTS -> DataError.RateLimited(
            retryAfterMillis = headers().parseRetryAfter(nowMillis),
            serverMessage = body,
        )
        code in 400..499 -> DataError.Http4xx(code, body)
        code in 500..599 -> DataError.Http5xx(code, body)
        else -> DataError.Unknown(body ?: "HTTP $code")
    }
}

/**
 * `Retry-After` may be either a number of seconds or an HTTP date. Both forms are
 * supported because providers are inconsistent; an invalid value falls back to the
 * exponential policy.
 */
fun Headers.parseRetryAfter(nowMillis: Long): Long? {
    val raw = get("Retry-After")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toLongOrNull()?.let { seconds ->
        return TimeUnit.SECONDS.toMillis(seconds.coerceAtLeast(0))
    }
    return runCatching {
        val target = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        (target - nowMillis).coerceAtLeast(0)
    }.getOrNull()
}

private const val HTTP_TOO_MANY_REQUESTS = 429
