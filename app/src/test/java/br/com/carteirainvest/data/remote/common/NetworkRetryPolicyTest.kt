package br.com.carteirainvest.data.remote.common

import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.Resource
import br.com.carteirainvest.testutil.RecordingSleeper
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class NetworkRetryPolicyTest {

    private val sleeper = RecordingSleeper()

    @Test
    fun `honours Retry-After on HTTP 429`() = runTest {
        val policy = policy(maxAttempts = 2)
        var calls = 0

        val result = policy.execute<Unit> {
            calls += 1
            throw httpException(code = 429, retryAfter = "2")
        }

        assertEquals(2, calls)
        assertTrue(result is Resource.Failure)
        val error = (result as Resource.Failure).error
        assertEquals(DataError.RateLimited(2_000L, """{"error":true}"""), error)
        assertEquals(listOf(2_000L), sleeper.delays)
    }

    @Test
    fun `retries HTTP 5xx with exponential backoff`() = runTest {
        val policy = policy(maxAttempts = 3, baseDelayMillis = 100)
        var calls = 0

        val result = policy.execute<Unit> {
            calls += 1
            throw httpException(code = 503)
        }

        assertEquals(3, calls)
        assertTrue(result is Resource.Failure)
        assertEquals(100L, sleeper.delays[0])
        assertEquals(200L, sleeper.delays[1])
    }

    @Test
    fun `does not retry HTTP 4xx`() = runTest {
        val policy = policy(maxAttempts = 3)
        var calls = 0

        val result = policy.execute<Unit> {
            calls += 1
            throw httpException(code = 404)
        }

        assertEquals(1, calls)
        assertTrue(result is Resource.Failure)
        assertEquals(0, sleeper.delays.size)
    }

    @Test
    fun `returns success after a transient failure`() = runTest {
        val policy = policy(maxAttempts = 2, baseDelayMillis = 50)
        var calls = 0

        val result = policy.execute {
            calls += 1
            if (calls == 1) throw httpException(code = 500)
            "ok"
        }

        assertEquals(2, calls)
        assertEquals(Resource.Success("ok"), result)
        assertEquals(listOf(50L), sleeper.delays)
    }

    private fun policy(
        maxAttempts: Int,
        baseDelayMillis: Long = 10L,
    ) = NetworkRetryPolicy(
        sleeper = sleeper,
        config = RetryConfig(
            maxAttempts = maxAttempts,
            baseDelayMillis = baseDelayMillis,
            maxDelayMillis = 10_000L,
            jitterRatio = 0.0,
        ),
        random = kotlin.random.Random(1),
    )

    private fun httpException(code: Int, retryAfter: String? = null): HttpException {
        val raw = Response.Builder()
            .request(Request.Builder().url("https://example.com/api").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .apply {
                if (retryAfter != null) header("Retry-After", retryAfter)
            }
            .body("""{"error":true}""".toResponseBody("application/json".toMediaType()))
            .build()
        val body = """{"error":true}""".toResponseBody("application/json".toMediaType())
        return HttpException(retrofit2.Response.error<Any>(body, raw))
    }
}
