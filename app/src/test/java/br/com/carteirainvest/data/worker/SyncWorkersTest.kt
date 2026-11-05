package br.com.carteirainvest.data.worker

import br.com.carteirainvest.data.sync.MarketSynchronizer
import br.com.carteirainvest.data.sync.SyncOutcome
import br.com.carteirainvest.data.notification.SyncFailureReporter
import br.com.carteirainvest.domain.model.DataError
import br.com.carteirainvest.domain.model.userMessage
import androidx.work.ListenableWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val NOTIFY_AFTER_ATTEMPTS = 2

class SyncWorkersTest {

    private lateinit var synchronizer: FakeMarketSynchronizer
    private lateinit var failureReporter: FakeSyncFailureReporter

    @Before
    fun setUp() {
        synchronizer = FakeMarketSynchronizer()
        failureReporter = FakeSyncFailureReporter()
    }

    @Test
    fun mapsSuccessToResultSuccessAndClearsFailure() = runTest {
        val outcome = SyncOutcome.Success(updated = 3)
        val result = executeSync("B3", failureReporter, 0) { outcome }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, failureReporter.notifyFailureCalls)
        assertTrue(failureReporter.clearCalled)
    }

    @Test
    fun mapsFailureToResultRetry() = runTest {
        val outcome = SyncOutcome.Failure(DataError.NoConnection)
        val result = executeSync("B3", failureReporter, 0) { outcome }

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(0, failureReporter.notifyFailureCalls)
    }

    @Test
    fun mapsStaleToResultRetry() = runTest {
        val outcome = SyncOutcome.Stale(DataError.NoConnection)
        val result = executeSync("câmbio", failureReporter, 0) { outcome }

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    private suspend fun executeSync(
        label: String,
        failureReporter: SyncFailureReporter,
        runAttemptCount: Int,
        block: suspend () -> SyncOutcome,
    ): ListenableWorker.Result = when (val outcome = block()) {
        is SyncOutcome.Success -> {
            failureReporter.clear()
            ListenableWorker.Result.success()
        }

        is SyncOutcome.Stale -> {
            if (runAttemptCount >= NOTIFY_AFTER_ATTEMPTS) {
                failureReporter.notifyFailure(label, outcome.error.userMessage("sincronizar $label"))
            }
            ListenableWorker.Result.retry()
        }

        is SyncOutcome.Failure -> {
            if (runAttemptCount >= NOTIFY_AFTER_ATTEMPTS) {
                failureReporter.notifyFailure(label, outcome.error.userMessage("sincronizar $label"))
            }
            ListenableWorker.Result.retry()
        }
    }

    private class FakeMarketSynchronizer : MarketSynchronizer {
        override suspend fun syncB3(force: Boolean): SyncOutcome = SyncOutcome.Success()
        override suspend fun syncCrypto(force: Boolean): SyncOutcome = SyncOutcome.Success()
        override suspend fun syncFx(force: Boolean): SyncOutcome = SyncOutcome.Success()
    }

    private class FakeSyncFailureReporter : SyncFailureReporter {
        var notifyFailureCalls = 0
        var clearCalled = false

        override fun notifyFailure(label: String, message: String) {
            notifyFailureCalls++
        }

        override fun clear() {
            clearCalled = true
        }
    }
}
