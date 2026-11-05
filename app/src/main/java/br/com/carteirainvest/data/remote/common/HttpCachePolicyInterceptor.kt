package br.com.carteirainvest.data.remote.common

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp's disk cache is a secondary layer. Room remains the source of truth and applies
 * the business TTL, so API requests ask OkHttp to revalidate instead of silently serving
 * a cached body that could bypass the repository's staleness rules.
 */
class HttpCachePolicyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        val revalidating = request.newBuilder()
            .header("Cache-Control", "no-cache")
            .build()
        return chain.proceed(revalidating)
    }
}
