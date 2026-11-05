package br.com.carteirainvest.data.remote.awesomeapi

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * AwesomeAPI FX client. It is keyless at the basic level, so no BuildConfig field is
 * required. PAIRS are written as `USD-BRL,EUR-BRL` and the response is keyed by the
 * concatenated code (`USDBRL`).
 */
interface AwesomeApi {
    @GET("json/last/{pairs}")
    suspend fun getLast(
        @Path("pairs", encoded = true) pairs: String,
    ): Map<String, AwesomeRateDto>
}
