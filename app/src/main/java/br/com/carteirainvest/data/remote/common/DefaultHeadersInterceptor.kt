package br.com.carteirainvest.data.remote.common

import br.com.carteirainvest.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Headers shared by every provider. The CoinGecko demo key is only added when it exists
 * in BuildConfig, so the app keeps working keyless at the basic tier.
 */
class DefaultHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "CarteiraInvest/1.0 (Android)")

        if (
            request.url.host.contains("coingecko", ignoreCase = true) &&
            BuildConfig.COINGECKO_API_KEY.isNotBlank()
        ) {
            builder.header("x-cg-demo-api-key", BuildConfig.COINGECKO_API_KEY)
        }
        return chain.proceed(builder.build())
    }
}
