package br.com.carteirainvest.testutil

import br.com.carteirainvest.core.util.TimeProvider

class TestTimeProvider(var now: Long = 1_700_000_000_000L) : TimeProvider {
    override fun nowMillis(): Long = now
}
