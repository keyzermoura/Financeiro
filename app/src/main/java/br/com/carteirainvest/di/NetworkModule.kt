package br.com.carteirainvest.di

import android.content.Context
import android.util.Log
import br.com.carteirainvest.BuildConfig
import br.com.carteirainvest.core.util.SystemTimeProvider
import br.com.carteirainvest.core.util.TimeProvider
import br.com.carteirainvest.data.remote.awesomeapi.AwesomeApi
import br.com.carteirainvest.data.remote.brapi.BrapiApi
import br.com.carteirainvest.data.remote.coingecko.CoinGeckoApi
import br.com.carteirainvest.data.remote.common.ApiCallExecutor
import br.com.carteirainvest.data.remote.common.DefaultHeadersInterceptor
import br.com.carteirainvest.data.remote.common.DefaultSleeper
import br.com.carteirainvest.data.remote.common.HttpCachePolicyInterceptor
import br.com.carteirainvest.data.remote.common.NetworkRetryPolicy
import br.com.carteirainvest.data.remote.common.RetryConfig
import br.com.carteirainvest.data.remote.common.Sleeper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024L * 1024L
    private const val HTTP_CACHE_DIR = "http_cache"

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Explicit timeouts prevent a dead provider from holding a Worker or a pull-to-refresh
     * forever. The disk cache is a secondary layer; Room still owns the offline contract.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE_BYTES))
            .addInterceptor(DefaultHeadersInterceptor())
            .addInterceptor(HttpCachePolicyInterceptor())

        if (BuildConfig.DEBUG) {
            val logger = HttpLoggingInterceptor.Logger { message ->
                Log.d("CarteiraHttp", redactSecrets(message))
            }
            builder.addInterceptor(
                HttpLoggingInterceptor(logger).apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                },
            )
        }
        return builder.build()
    }

    private fun redactSecrets(message: String): String {
        var safe = message
        if (BuildConfig.BRAPI_TOKEN.isNotBlank()) {
            safe = safe.replace(BuildConfig.BRAPI_TOKEN, "***")
        }
        if (BuildConfig.COINGECKO_API_KEY.isNotBlank()) {
            safe = safe.replace(BuildConfig.COINGECKO_API_KEY, "***")
        }
        return safe
            .replace(Regex("(?i)(token|api[-_]?key)=([^&\\s]+)"), "$1=***")
            .replace(
                Regex("(?i)(x-cg-demo-api-key|x-cg-pro-api-key|authorization):\\s*\\S+"),
                "$1: ***",
            )
    }

    @Provides
    @Singleton
    fun provideBrapiApi(client: OkHttpClient, gson: Gson): BrapiApi =
        retrofit("https://brapi.dev/api/", client, gson).create(BrapiApi::class.java)

    @Provides
    @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient, gson: Gson): CoinGeckoApi =
        retrofit("https://api.coingecko.com/api/v3/", client, gson)
            .create(CoinGeckoApi::class.java)

    @Provides
    @Singleton
    fun provideAwesomeApi(client: OkHttpClient, gson: Gson): AwesomeApi =
        retrofit("https://economia.awesomeapi.com.br/", client, gson)
            .create(AwesomeApi::class.java)

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun provideSleeper(): Sleeper = DefaultSleeper()

    @Provides
    @Singleton
    fun provideRetryConfig(): RetryConfig = RetryConfig()

    @Provides
    @Singleton
    fun provideApiCallExecutor(
        sleeper: Sleeper,
        config: RetryConfig,
    ): ApiCallExecutor = NetworkRetryPolicy(sleeper, config)

    private fun retrofit(baseUrl: String, client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
}
