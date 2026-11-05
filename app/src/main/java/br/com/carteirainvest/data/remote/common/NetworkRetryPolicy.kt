package br.com.carteirainvest.data.remote.common

import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/** Small abstraction that makes retry timing testable without real waits. */
fun interface Sleeper {
    suspend fun sleep(millis: Long)
}

class DefaultSleeper : Sleeper {
    override suspend fun sleep(millis: Long) {
        if (millis > 0) delay(millis)
    }
}

data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 500L,
    val maxDelayMillis: Long = 8_000L,
    val jitterRatio: Double = 0.2,
)

/**
 * Executes a suspending API call and decides whether it should be retried.
 *
 * Policy:
 * - network failures, timeouts, HTTP 5xx and HTTP 429 are retryable;
 * - HTTP 4xx (except 429) and parse errors fail immediately;
 * - HTTP 429 honours `Retry-After` when present, otherwise uses exponential backoff;
 * - backoff is exponential (500 ms, 1 s, 2 s...) capped at 8 s, with 20% jitter.
 *
 * The method never throws for transport failures. Cancellation is rethrown so
 * structured concurrency keeps working.
 */
interface ApiCallExecutor {
    suspend fun <T> execute(block: suspend () -> T): Resource<T>
}

class NetworkRetryPolicy(
    private val sleeper: Sleeper,
    private val config: RetryConfig = RetryConfig(),
    private val random: Random = Random.Default,
) : ApiCallExecutor {

    override suspend fun <T> execute(block: suspend () -> T): Resource<T> {
        var lastError: DataError? = null
        repeat(config.maxAttempts) { attempt ->
            try {
                return Resource.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val dataError = error.toDataError()
                lastError = dataError
                val isLastAttempt = attempt == config.maxAttempts - 1
                if (!dataError.isRetryable || isLastAttempt) {
                    return Resource.Failure(dataError)
                }
                val wait = when (dataError) {
                    is DataError.RateLimited ->
                        dataError.retryAfterMillis ?: backoffMillis(attempt)
                    else -> backoffMillis(attempt)
                }
                sleeper.sleep(wait)
            }
        }
        return Resource.Failure(lastError ?: DataError.Unknown())
    }

    private fun backoffMillis(attempt: Int): Long {
        val raw = config.baseDelayMillis * 2.0.pow(attempt)
        val capped = raw.coerceAtMost(config.maxDelayMillis.toDouble())
        val jitter = capped * config.jitterRatio * (random.nextDouble() * 2.0 - 1.0)
        return (capped + jitter).toLong().coerceAtLeast(0)
    }
}
