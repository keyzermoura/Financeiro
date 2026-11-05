package br.com.carteirainvest.core.util

/**
 * Injectable clock. Repositories use it to derive staleness from Room's `updatedAt`
 * without touching `System.currentTimeMillis()` directly, which keeps TTL tests
 * deterministic.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
