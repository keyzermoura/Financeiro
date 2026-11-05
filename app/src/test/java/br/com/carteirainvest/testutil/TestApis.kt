package br.com.carteirainvest.testutil

import br.com.carteirainvest.data.remote.awesomeapi.AwesomeApi
import br.com.carteirainvest.data.remote.brapi.BrapiApi
import br.com.carteirainvest.data.remote.coingecko.CoinGeckoApi
import com.google.gson.GsonBuilder
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

fun brapiApi(server: MockWebServer): BrapiApi = retrofit(server).create(BrapiApi::class.java)

fun coinGeckoApi(server: MockWebServer): CoinGeckoApi =
    retrofit(server).create(CoinGeckoApi::class.java)

fun awesomeApi(server: MockWebServer): AwesomeApi =
    retrofit(server).create(AwesomeApi::class.java)

private fun retrofit(server: MockWebServer): Retrofit =
    Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()
