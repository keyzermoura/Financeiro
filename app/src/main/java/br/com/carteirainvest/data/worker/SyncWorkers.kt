package br.com.carteirainvest.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import br.com.carteirainvest.data.notification.SyncFailureReporter
import br.com.carteirainvest.data.sync.MarketSynchronizer
import br.com.carteirainvest.data.sync.SyncOutcome
import br.com.carteirainvest.domain.model.userMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One Worker per data type. Each one has its own unique periodic work, network
 * constraint, backoff and notification label, so a failing provider does not block the
 * other syncs.
 */
@HiltWorker
class B3SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: MarketSynchronizer,
    private val failureReporter: SyncFailureReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result =
        executeSync("B3", failureReporter, runAttemptCount) { synchronizer.syncB3() }
}

@HiltWorker
class CryptoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: MarketSynchronizer,
    private val failureReporter: SyncFailureReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result =
        executeSync("criptomoedas", failureReporter, runAttemptCount) { synchronizer.syncCrypto() }
}

@HiltWorker
class FxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: MarketSynchronizer,
    private val failureReporter: SyncFailureReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result =
        executeSync("câmbio", failureReporter, runAttemptCount) { synchronizer.syncFx() }
}

/**
 * Shared Worker policy:
 * - success cancels any old failure notification and returns `success`;
 * - stale cache or hard failure returns `retry`, letting WorkManager apply exponential
 *   backoff;
 * - the user is notified only after [NOTIFY_AFTER_ATTEMPTS] failed runs, never on routine
 *   success.
 */
internal suspend fun executeSync(
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

private const val NOTIFY_AFTER_ATTEMPTS = 2
