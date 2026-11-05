package br.com.carteirainvest.testutil

import br.com.carteirainvest.data.remote.common.Sleeper

class RecordingSleeper : Sleeper {
    val delays = mutableListOf<Long>()

    override suspend fun sleep(millis: Long) {
        delays += millis
    }
}
