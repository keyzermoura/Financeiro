package br.com.carteirainvest.data.remote.brapi

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Brapi free-tier client.
 *
 * The token is optional on the public tier but strongly recommended because the free
 * plan is limited to 15,000 requests/month and one concurrent request. The repository
 * serializes calls with a dedicated Mutex; the API itself must never be called directly
 * from the UI.
 */
interface BrapiApi {
    @GET("quote/{tickers}")
    suspend fun getQuotes(
        @Path("tickers", encoded = true) tickers: String,
        @Query("token") token: String? = null,
        @Query("range") range: String? = null,
        @Query("interval") interval: String? = null,
        @Query("dividends") dividends: Boolean? = null,
    ): BrapiQuoteResponse
}
