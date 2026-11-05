package br.com.carteirainvest.data.remote.coingecko

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CoinGecko public client. The demo API key is optional and injected by the common
 * headers interceptor when `COINGECKO_API_KEY` is present in BuildConfig.
 */
interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getSimplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrency: String = "brl",
        @Query("include_24hr_change") include24hChange: Boolean = true,
    ): Map<String, CoinGeckoPriceDto>
}
